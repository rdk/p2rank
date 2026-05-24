package cz.siret.prank.features.implementation.physics

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.ResidueFeatureCalculationContext
import cz.siret.prank.features.api.ResidueFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic

/**
 * Mean-Square Fluctuation (MSF) from the Anisotropic Network Model — the trace
 * of the per-residue 3×3 covariance block, equivalently
 *   MSF_j = Σ_a Σ_k u_k[3j+a]² / λ_k
 * over the kept non-trivial vibrational modes.
 *
 * Anisotropic Network Model (ANM):
 *   Atilgan, A.R. et al. (2001). Anisotropy of Fluctuation Dynamics of Proteins
 *   with an Elastic Network Model. Biophys. J. 80(1), 505-515.
 *   https://doi.org/10.1016/S0006-3495(01)76033-X
 *
 * Adaptation: MSF extracted from the same eigendecomposition used for PRS,
 * serving as an independent flexibility descriptor for P2Rank. Ligand binding
 * sites tend to exhibit intermediate flexibility — neither fully rigid nor
 * highly disordered.
 *
 * Shares the cached AnmModel with anm_sensor and anm_effectiveness.
 */
@CompileStatic
class AnmMsfRF extends ResidueFeatureCalculator implements Parametrized {

    static final String NAME = "anm_msf"

    @Override String getName() { NAME }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        AnmModel.getOrCompute(protein, params)
    }

    @Override
    double[] calculateForResidue(Residue residue, ResidueFeatureCalculationContext context) {
        AnmModel model = AnmModel.getOrCompute(context.protein, params)
        return [model.msfFor(residue)] as double[]
    }
}
