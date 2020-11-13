package cz.siret.prank.features.api

import cz.siret.prank.domain.Residue
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Extend this class to implement new feature that adds values directly to SAS feature vector (assigned to Solvent Accessible Surface points).
 *
 * Register implementation in FeatureRegistry.
 * To use feature in experiments add feature name (FeatureCalculator.getName()) to Params.features.
 */
@CompileStatic
abstract class SasFeatureCalculator extends AbstractFeatureCalculator {

    @Override
    FeatureCalculator.Type getType() {
        return FeatureCalculator.Type.SAS_POINT
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext protein) {
        throw new UnsupportedOperationException()
    }

    @Override
    double[] calculateForResidue(Residue residue, ResidueFeatureCalculationContext context) {
        throw new UnsupportedOperationException()
    }

}
