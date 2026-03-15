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

        if (item.originDataset.hasExplicitSites()) {
            ExplicitSitesIndex index = item.originDataset.explicitSitesIndex
            res.holoProtein.sites = (List<BindingSite>) index.resolveForProtein(res.holoProtein, item.proteinFile)
            log.info "Loaded {} explicit sites for [{}]", res.holoProtein.sites.size(), item.label
        } else {
            res.holoProtein.populateSitesFromLigands()
        }

        ProcessedItemContext itemContext = item.context

        if (params.identify_peptides_by_labeling) {
            loadPeptidesFromLabeling(res.holoProtein, itemContext)
        }

        return res
    }

//===========================================================================================================//

    /**
     * Loads peptides (chains not in the main=reduced structure) that are likely to be binding according to provided residue labeling.
     * Adds them to protein structure and to protein.peptides list.
     */
    private static void loadPeptidesFromLabeling(Protein prot, ProcessedItemContext ctx) {
        log.info 'loading peptides for {}', prot.name
        if (!ctx.dataset.hasExplicitResidueLabeling()) {
            throw new PrankException("No labeling provided for identify_peptides_by_labeling!")
        }
        BinaryLabeling labeling = ctx.dataset.explicitBinaryResidueLabeler.getBinaryLabeling(prot.residues, prot)

        prot.peptides = new ArrayList<>()
        for (Chain ch in prot.fullStructure.chains) {
            ResidueChain rc = Struct.toResidueChain(ch)
            log.info 'checking chain {} (len:{})', rc.authorId, rc.length

            if (rc.authorId in ctx.item.chains) {
                log.info 'chain {} is among specified chains in the dataset, skipping', rc.authorId
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

    static boolean isBindingPeptide(Chain candidateChain, Protein toProtein, BinaryLabeling labeling, ProcessedItemContext ctx) {
        double proteinPeptideBindingDist = 4.5d

        Atoms protAtoms = toProtein.proteinAtoms
        Residues positiveResidues = Residues.of(toProtein.residues.findAll { Residue it -> labeling.getLabel(it) })

        Atoms chainAtoms = Atoms.allFromChain(candidateChain).withoutHydrogens()
        Atoms contactChainAtoms = chainAtoms.cutoutShell(protAtoms, proteinPeptideBindingDist)

        if (contactChainAtoms.empty) {
            log.info 'no chain contact atoms'
            return false
        }
        int permissible = 0  // contact atoms near positive residues
        for (Atom a : contactChainAtoms) {
            if (positiveResidues.atoms.areWithinDistance(a, proteinPeptideBindingDist + 1.0d)) {
                permissible++
            } else {
                log.debug 'found contact atom {} that is far from positively labeled residues', a.PDBserial
            }
        }
        int n = contactChainAtoms.count
        double ratio = ((double)permissible) / n
        log.info 'contact_atoms_near_positive_res:{} contact_atoms:{} ratio:{}', permissible, n, ratio

        return ratio >= 0.5
    }

}
