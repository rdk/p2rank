package cz.siret.prank.features.implementation.conservation

import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group

/**
 * Simple single value SAS feature that adds "ptortusion" of protein surface to SAS feature vector.
 * Protrusion is simply a number of protein atoms in params.protrusion_radius around SAS point.
 */
@CompileStatic
class ConservationCloudFeature extends SasFeatureCalculator implements Parametrized {

    static final String NAME = "conservationcloud"

    @Override
    String getName() { NAME }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {

        // brute force ... O(N*M) where N is number of atoms and M number of Connolly points
        // deepSurrounding conmtains often nearly all of the protein atoms
        // and this is one of the most expensive part od the algorithm when making predictions
        // (apart from classification and Connolly surface generation)
        // better solution would be to build triangulation over protein atoms or to use KD-tree with range search
        // or at least some space compartmentalization

        // optimization? - we need ~250 for protrusion=10 and in this case it is sower
        //int MAX_PROTRUSION_ATOMS = 250
        //Atoms deepSurrounding = this.deepSurrounding.withKdTree().kdTree.findNearestNAtoms(point, MAX_PROTRUSION_ATOMS, false)

       Atoms surroundingAtoms = context.extractor.deepSurrounding.cutoffAroundAtom(sasPoint, params.protrusion_radius)
       double value = surroundingAtoms.getDistinctGroups().stream().mapToDouble({ Group group ->
            context.protein.conservationScore.getScoreForResidue(group.getResidueNumber())
        }).average().getAsDouble(); 
        return [value] as double[]
    }

}
