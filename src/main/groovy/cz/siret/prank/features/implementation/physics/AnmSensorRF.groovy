package cz.siret.prank.features.implementation.physics

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.ResidueFeatureCalculationContext
import cz.siret.prank.features.api.ResidueFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic

/**
 * PRS Sensor Intensity — column average of the perturbation-response scanning
 * matrix. Measures how strongly a residue responds to perturbations originating
 * from all other residues.
 *
 * Perturbation Response Scanning (PRS) method:
 *   Atilgan, C. & Atilgan, A.R. (2009). Perturbation-Response Scanning Reveals
 *   Ligand Entry-Exit Mechanisms of Ferric Binding Protein. PLoS Comput. Biol.
 *   5(10), e1000544. https://doi.org/10.1371/journal.pcbi.1000544
 *
 * Applied as a residue-level feature for allosteric site prediction in:
 *   Ke, X. et al. (2026). ZHMolEReP: An Energy Response Strategy for Protein
 *   Allosteric Site Prediction. J. Chem. Inf. Model.
 *   https://doi.org/10.1021/acs.jcim.6c00141
 *
 * Adaptation: used here without a predefined active site — sensor values are
 * computed globally (averaged over all perturbation sources) and served as
 * per-residue features for ligand binding site prediction in P2Rank.
 *
 * Implementation: shares the cached AnmModel built in preProcessProtein with
 * the sibling features anm_effectiveness and anm_msf so that the underlying
 * eigendecomposition runs exactly once per protein.
 */
@CompileStatic
class AnmSensorRF extends ResidueFeatureCalculator implements Parametrized {

    static final String NAME = "anm_sensor"

    @Override String getName() { NAME }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        AnmModel.getOrCompute(protein, params)
    }

    @Override
    double[] calculateForResidue(Residue residue, ResidueFeatureCalculationContext context) {
        AnmModel model = AnmModel.getOrCompute(context.protein, params)
        return [model.sensorFor(residue)] as double[]
    }
}
