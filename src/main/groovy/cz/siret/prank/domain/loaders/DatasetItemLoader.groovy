package cz.siret.prank.domain.loaders

import cz.siret.prank.domain.*
import cz.siret.prank.domain.labeling.BinaryLabeling
import cz.siret.prank.domain.loaders.pockets.PredictionLoader
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.implementation.conservation.ConservationScore
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Cutils
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Chain

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Function

/**
 *
 */
@Slf4j
@CompileStatic
class DatasetItemLoader implements Parametrized, Writable {

    private final LoaderParams loaderParams
    private final @Nullable PredictionLoader predictionLoader

    DatasetItemLoader(LoaderParams loaderParams, @Nullable PredictionLoader predictionLoader) {
        this.loaderParams = loaderParams
        this.predictionLoader = predictionLoader
    }

    /**
     * Loader for dataset row. Loads protein and existing pocket prediction (if provided).
     *
     * @param proteinFile path to protein (could be just query pdb file with no ligands when rescoring, or control protein with ligands on evaluation)
     * @param predictionFile main pocket prediction output file (from the second column in the dataset file)
     * @return
     */
    PredictionPair loadPredictionPair(@Nonnull Dataset.Item item) {
        PredictionPair res = new PredictionPair()
        res.name = Futils.shortName(item.proteinFile)
        res.holoProtein = Protein.load(item.proteinFile, item.chains, loaderParams)

        if (item.apoProteinFile != null) {
            Protein apo = Protein.load(item.apoProteinFile, item.apoChains, loaderParams)
            apo.apoStructure = true
            apo.apoLigands = apo.ligands
            apo.ligands = res.holoProtein.ligands
            res.apoProtein = apo
        }

        if (item.pocketPredictionFile != null) {
            res.prediction = predictionLoader.loadPrediction(item.pocketPredictionFile, res.holoProtein)
        } else {
            res.prediction = new Prediction(res.protein, [])
        }

        ProcessedItemContext itemContext = item.context

        // TODO: move conservation related stuff to feature implementation
        if (loaderParams.load_conservation) {
            loadConservationScores(item.proteinFile, itemContext, res)
        }

        if (params.identify_peptides_by_labeling) {
            loadPeptidesFromLabeling(res.holoProtein, itemContext)
        }

        return res
    }

//===========================================================================================================//

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

            if (isBindingPeptide(ch, prot, labeling, ctx)) {
                prot.structure.addChain(ch)
                prot.peptides.add(rc)
                prot.relevantLigands.add new Ligand(Atoms.allFromChain(ch), prot)
                log.info 'adding binding peptide {}', rc.authorId
            } else {
                log.info 'refused peptide {} as non binding', rc.authorId
            }

        }
    }

    boolean isBindingPeptide(Chain chain, Protein toProtein, BinaryLabeling labeling, ProcessedItemContext ctx) {
        Atoms protAtoms = toProtein.getResidueChain(ctx.item.chains.first()).atoms
        Residues labeledRes = new Residues(toProtein.residues.findAll { labeling.getLabel((Residue)it) }.asList() as List<Residue>)

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

        return ratio >= 0.5
    }

//===========================================================================================================//

    @Nullable
    private File findConservationFile(List<String> dirs, String proteinFile, String chainId) {
        log.info "Looking for conservation in dirs {}", dirs

        String baseName = Futils.baseName(proteinFile)

        String prefix = baseName + '_' + chainId + '.'  // e.g. "2ed4_A."
        File res = findConservFilePrefixed(dirs, prefix)

        if (res == null) { // try old prefix format without '_'
            prefix = baseName + chainId + '.'           // e.g. "2ed4A."
            res = findConservFilePrefixed(dirs, prefix)
        }

        if (res != null) {
            log.info "Conservation file for [{}] found: [{}]", prefix, res?.absolutePath
        } else {
            log.info "Conservation file for [{}] not found", prefix, res?.absolutePath
        }

        return res
    }

    private File findConservFilePrefixed(List<String> dirs, String prefix) {
        return Futils.findFileInDirs(dirs, {File f ->
            f.name.startsWith(prefix) && (Futils.realExtension(f.name) == "hom")
        })
    }

    private checkConservationDirsExist(List<String> dirs) {
        for (String dir : dirs) {
            if (!Futils.exists(dir)) {
                throw new PrankException("Directory defined in 'conservation_dirs' param doesn't exist: " + dir)
            }
        }
    }

    private List<String> getConservationLookupDirs(String proteinFile, ProcessedItemContext itemContext) {

        if (!Cutils.empty(params.conservation_dirs)) {
            String datasetDir = itemContext.item.originDataset.dir
            List<String> dirs = params.conservation_dirs.collect {Futils.prependIfNotAbsolute(it, datasetDir) }
            return dirs
        } else {
            String pdbDir = Futils.dir(proteinFile)
            return [pdbDir]
        }
    }

    private loadConservationScores(String proteinFile, ProcessedItemContext itemContext, PredictionPair pair) {

        String conservColumn = itemContext.datsetColumnValues.get(Dataset.COLUMN_CONSERVATION_FILES_PATTERN)

        Function<String, File> conservationFinder // maps chain ids to files
        if (conservColumn == null) {
            List<String> conservDirs = getConservationLookupDirs(proteinFile, itemContext)
            log.info "Conservation lookup dirs: " + conservDirs
            checkConservationDirsExist(conservDirs)

            conservationFinder = { String chainId -> findConservationFile(conservDirs, proteinFile, chainId) }
        } else {
            Path parentDir = Paths.get(proteinFile).parent
            String pattern = conservColumn
            conservationFinder = { String chainId ->
                parentDir.resolve(pattern.replaceAll("%chainID%", chainId)).toFile()
            }
        }
        // TODO use itemContext attribute instead
        itemContext.auxData.put(ConservationScore.CONSERV_PATH_FUNCTION_KEY, conservationFinder)

        pair.protein.loadConservationScores(itemContext)
    }

}
