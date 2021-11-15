package cz.siret.prank.features.implementation

import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Simple single value SAS feature that adds "protrusion" of protein surface to SAS feature vector.
 * Protrusion is simply a number of protein atoms in variables.protrusion_radius around SAS point.
 */
@CompileStatic
class ProtrusionFeature extends SasFeatureCalculator implements Parametrized {

    static final String NAME = "protrusion"

    @Override
    String getName() { NAME }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {

        // brute force ... O(N*M) where N is number of atoms and M number of SAS points
        // deepLayer contains often nearly all of the protein atoms
        // and this is one of the most expensive part od the algorithm when making predictions
        // (apart from classification and SAS surface generation)
        // better solution would be to build triangulation over protein atoms or to use KD-tree with range search
        // or at least some space compartmentalization

        // optimization? - we need ~250 for protrusion=10 and in this case it is slower
        //int MAX_PROTRUSION_ATOMS = 250
        //Atoms deepLayer = this.deepLayer.withKdTree().kdTree.findNearestNAtoms(point, MAX_PROTRUSION_ATOMS, false)

        double protAtoms = context.extractor.deepLayer.cutoutSphere(sasPoint, params.protrusion_radius).count  // deepLayer is previously generated in PrankFeatureExtractor, depth is params.protrusion_radius
        return [protAtoms] as double[]
    }

}
