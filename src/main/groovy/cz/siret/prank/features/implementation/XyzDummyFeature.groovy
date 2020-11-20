package cz.siret.prank.features.implementation

import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Dummy feature that produces point coordinates
 * (potentially useful for external dataset analysis)
 */
@CompileStatic
class XyzDummyFeature extends SasFeatureCalculator implements Parametrized {

    @Override
    String getName() {
        return "xyz"
    }

    @Override
    List<String> getHeader() {
        return ["x","y","z"]
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        return [sasPoint.x, sasPoint.y, sasPoint.z] as double[]
    }
    
}
