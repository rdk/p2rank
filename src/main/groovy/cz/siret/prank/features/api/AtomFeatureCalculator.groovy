package cz.siret.prank.features.api

import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 *
 */
@CompileStatic
abstract class AtomFeatureCalculator extends AbstractFeatureCalculator {

    @Override
    FeatureCalculator.Type getType() {
        return FeatureCalculator.Type.ATOM
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        throw new UnsupportedOperationException()
    }

}
