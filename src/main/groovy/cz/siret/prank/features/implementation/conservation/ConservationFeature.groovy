package cz.siret.prank.features.implementation.conservation

import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.GroupType

/**
 * Simple single value Conservation feature that adds conservation from evolution from HSSP database or conservation pipeline to Atom feature vector.
 */
@CompileStatic
class ConservationFeature extends AtomFeatureCalculator {

    @Override
    String getName() {
        "conservation"
    }

    private static Random rand = Random.newInstance();

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext protein) {
        Group parentAA = proteinSurfaceAtom.getGroup()
        if (parentAA.getType() != GroupType.AMINOACID) {
            return [0.0] as double[]
        }

        double value = protein.protein.conservationScore.getScoreForResidue(parentAA.getResidueNumber())
        //double value = rand.nextDouble();
        return [value] as double[]
    }

}
