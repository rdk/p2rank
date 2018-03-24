package cz.siret.prank.features.api

import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * so far residue features are not fully integrated
 */
@CompileStatic
abstract class ResidueFeatureCalculator extends AbstractFeatureCalculator {

    @Override
    FeatureCalculator.Type getType() {
        return FeatureCalculator.Type.RESIDUE
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext protein) {
        throw new UnsupportedOperationException()
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        throw new UnsupportedOperationException()
    }

}
