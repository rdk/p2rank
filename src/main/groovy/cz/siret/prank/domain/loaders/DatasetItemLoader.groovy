package cz.siret.prank.domain.loaders

import cz.siret.prank.domain.*
import cz.siret.prank.domain.labeling.BinaryLabeling
import cz.siret.prank.domain.loaders.pockets.PredictionLoader
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Chain

import javax.annotation.Nonnull
import javax.annotation.Nullable

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
        res.holoProtein = Protein.load(item.proteinFile, item.chains, loaderParams).transformed(item.transformation)

        if (item.apoProteinFile != null) {
            Protein apo = Protein.load(item.apoProteinFile, item.apoChains, loaderParams).transformed(item.transformation)
            apo.apoStructure = true
            apo.apoLigands = apo.ligands
            apo.ligands = res.holoProtein.ligands
            res.apoProtein = apo
        }

        if (item.pocketPredictionFile != null) {
            log.info "Loading pocket predictions from [$item.pocketPredictionFile] using ${predictionLoader.class.simpleName}"
            res.prediction = predictionLoader.withTransformation(item.transformation).loadPrediction(item.pocketPredictionFile, res.holoProtein)
            log.info "Loaded ${res.prediction.pockets.size()} predicted pockets"
        } else {
            res.prediction = new Prediction(res.protein, [])
        }

        ProcessedItemContext itemContext = item.context

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

}
