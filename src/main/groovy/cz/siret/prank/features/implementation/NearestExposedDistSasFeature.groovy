package cz.siret.prank.features.implementation


import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Distance of SAS point to the nearest solvent exposed exposed protein atom.
 */
@CompileStatic
class NearestExposedDistSasFeature extends SasFeatureCalculator {

    @Override
    String getName() {
        return 'exposed_dist'
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        double dist = context.protein.exposedAtoms.dist(sasPoint)
        return [dist] as double[]
    }
    
}
