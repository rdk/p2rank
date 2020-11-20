package cz.siret.prank.features.implementation

import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 *  Similar as ProtrusionFeature but takes number of protein atoms only from protein solvent exposed atoms.
 */
@CompileStatic
class SurfaceProtrusionFeature extends SasFeatureCalculator implements Parametrized {

    @Override
    String getName() {
        return "surface_protrusion"
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        double protAtoms = context.protein.exposedAtoms.cutoutSphere(sasPoint, params.protrusion_radius).count
        return [protAtoms] as double[]
    }

}
