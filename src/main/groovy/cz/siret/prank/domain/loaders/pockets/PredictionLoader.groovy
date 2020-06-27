package cz.siret.prank.domain.loaders.pockets

import javax.annotation.Nullable
import cz.siret.prank.domain.*
import cz.siret.prank.domain.labeling.BinaryLabeling
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.implementation.conservation.ConservationScore
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Chain

import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Function

/**
 * Base class for prediction loaders (parsers for predictions produced by pocket prediction tools)
 * Also serves as a protein/dataset-item loader even if there is no prediction (TODO: refactor).
 */
@Slf4j
abstract class PredictionLoader implements Parametrized {

    LoaderParams loaderParams = new LoaderParams()

    /**
     *
     * @param queryProteinFile path to protein (could be just query pdb file with no ligands when rescoring, or control protein with ligands on evaluation)
     * @param predictionOutputFile main pocket prediction output file (from the second column in the dataset file)
     * @return
     */
    PredictionPair loadPredictionPair(String queryProteinFile, String predictionOutputFile,
                                      ProcessedItemContext itemContext) {
        File protf = new File(queryProteinFile)

        PredictionPair res = new PredictionPair()
        res.name = protf.name

        if (itemContext.item.hasSpecifiedChaids()) {
            res.protein = Protein.loadReduced(queryProteinFile, loaderParams, itemContext.item.getChains())
        } else {
            res.protein = Protein.load(queryProteinFile, loaderParams)
        }

        if (predictionOutputFile != null) {
            res.prediction = loadPrediction(predictionOutputFile, res.protein)
        } else {
            res.prediction = new Prediction(res.protein, [])
        }

        // TODO: move conservation related stuff to feature implementation
        if (loaderParams.load_conservation_paths) {
            loadConservationScores(queryProteinFile, itemContext, res)
        }

        if (params.identify_peptides_by_labeling) {
            loadPeptidesFromLabeling(res.protein, itemContext)
        }
        
        return res
    }

    private loadPeptidesFromLabeling(Protein prot, ProcessedItemContext ctx) {
        log.info 'loading peptides for {}', prot.name
        if (!ctx.dataset.hasExplicitResidueLabeling()) {
            throw new PrankException("No labeling provided for identify_peptides_by_labeling!")
        }
        BinaryLabeling labeling = ctx.dataset.explicitBinaryResidueLabeler.getBinaryLabeling(prot.residues, prot)

        for (Chain ch in prot.fullStructure.chains) {
            ResidueChain rc = Struct.toResidueChain(ch)
            log.info 'checking chain {} (len:{})', rc.authorId, rc.length

            if (rc.authorId in ctx.item.chains) {
                log.info 'is among selected chains in the dataset, skipping'
                continue
            }
            //if (rc.length > 30) {
            //    log.info 'longer than 30 res., skipping'
            //    continue
            //}

            if (isBindingPeptide(ch, prot, labeling, ctx)) {
                prot.structure.addChain(ch)
                prot.peptides.add(rc)
                prot.ligands.add new Ligand(Atoms.allFromChain(ch), prot)
                log.info 'adding bindng peptide {}', rc.authorId
            } else {
                log.info 'refused peptide {} as non binding', rc.authorId
            }

        }
    }

    boolean isBindingPeptide(Chain chain, Protein toProtein, BinaryLabeling labeling, ProcessedItemContext ctx) {
        Atoms protAtoms = toProtein.getResidueChain(ctx.item.chains.first()).atoms
        Residues labeledRes = new Residues(toProtein.residues.findAll { labeling.getLabel(it) }.asList())


        Atoms chainAtoms = Atoms.allFromChain(chain).withoutHydrogens()
        Atoms contactChainAtoms = chainAtoms.cutoutShell(protAtoms, 3.5d)

        if (contactChainAtoms.empty) {
            log.info 'no chain contact atoms'
            return false
        }
        int permissible = 0
        for (Atom a : contactChainAtoms) {
            if (labeledRes.atoms.areWithinDistance(a, 3.5d)) {
                permissible++
            } else {
                log.info 'found contact atom not close to contact res'
            }
        }
        int n = contactChainAtoms.count
        double ratio = ((double)permissible) / n
        log.info 'permissible_a:{} contact_a:{} ratio:{}', permissible, n, ratio

        if (ratio >= 0.5) {
            return true
        } else {
            return false
        }

        //log.info 'chain:{} binding_res:{} labeled_res:{}', chain.id, bindingRes.size(), labeledRes.size()
        //return bindingRes.size()>0 && labeledRes.containsAll(bindingRes)
    }


    private loadConservationScores(String queryProteinFile, ProcessedItemContext itemContext, PredictionPair res) {
        Path parentDir = Paths.get(queryProteinFile).parent

        if (params.conservation_dir != null) {
            String baseDir = itemContext.item.dataset.dir
            String conservDir = baseDir + "/" + params.conservation_dir
            parentDir = Paths.get(conservDir)
        }
        log.info "Conservation parent dir: " + parentDir

        String conservColumn = itemContext.datsetColumnValues.get(Dataset.COLUMN_CONSERVATION_FILES_PATTERN)

        Function<String, File> conserPathForChain // maps chain ids to files
        if (conservColumn == null) {
            log.info("Setting conservation path. Origin: {}", loaderParams.conservation_origin)

            conserPathForChain = { String chainId ->
                parentDir.resolve(ConservationScore.scoreFileForPdbFile(
                        Futils.shortName(queryProteinFile), chainId, loaderParams.conservation_origin)
                ).toFile()
            }
        } else {
            String pattern = conservColumn
            conserPathForChain = { String chainId ->
                parentDir.resolve(pattern.replaceAll("%chainID%", chainId)).toFile()
            }
        }
        // TODO use itemContext attribute instead
        itemContext.auxData.put(ConservationScore.CONSERV_PATH_FUNCTION_KEY, conserPathForChain)

        if (loaderParams.load_conservation) {
            res.protein.loadConservationScores(itemContext)
        }
    }

    /**
     * @param predictionOutputFile main pocket prediction output file
     * @param protein to which this prediction is related. may be null!
     * @return
     */
    abstract Prediction loadPrediction(String predictionOutputFile,
                                       @Nullable Protein liganatedProtein)

    /**
     * used when running 'prank rescore' on a dataset with one column 'predictionOutputFile'
     * @param predictionOutputFile
     * @return
     */
    Prediction loadPredictionWithoutProtein(String predictionOutputFile) {
        loadPrediction(predictionOutputFile, null)
    }

}
