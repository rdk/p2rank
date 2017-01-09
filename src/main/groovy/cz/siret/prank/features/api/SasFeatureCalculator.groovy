package cz.siret.prank.features.api

import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 *
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

}
