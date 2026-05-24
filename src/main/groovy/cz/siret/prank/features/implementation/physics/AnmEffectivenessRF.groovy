package cz.siret.prank.features.implementation.physics

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.ResidueFeatureCalculationContext
import cz.siret.prank.features.api.ResidueFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic

/**
 * PRS Effectiveness — row average of the perturbation-response scanning matrix.
 * Measures how strongly a residue's perturbation propagates to all others.
 *
 * Same PRS framework as anm_sensor (see citation there):
 *   Atilgan, C. & Atilgan, A.R. (2009). PLoS Comput. Biol. 5(10), e1000544.
 *
 * Adaptation: used as an independent per-residue feature alongside the sensor
 * for ligand binding site prediction. Binding sites often score high on both
 * effectiveness (signal transmission) and sensor (signal reception).
 *
 * Shares the cached AnmModel with anm_sensor and anm_msf.
 */
@CompileStatic
class AnmEffectivenessRF extends ResidueFeatureCalculator implements Parametrized {

    static final String NAME = "anm_effectiveness"

    @Override String getName() { NAME }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        AnmModel.getOrCompute(protein, params)
    }

    @Override
    double[] calculateForResidue(Residue residue, ResidueFeatureCalculationContext context) {
        AnmModel model = AnmModel.getOrCompute(context.protein, params)
        return [model.effectivenessFor(residue)] as double[]
    }
}
