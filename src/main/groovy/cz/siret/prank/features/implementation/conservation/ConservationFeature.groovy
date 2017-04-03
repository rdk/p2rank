package cz.siret.prank.features.implementation.conservation

import cz.siret.prank.domain.Protein
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
    void preProcessProtein(Protein protein) {
        // Check if conservation is already loaded.
        if (protein.secondaryData.getOrDefault(ConservationScore.conservationLoadedKey, false)) {
            // Load conservation score.
            ConservationScore score = ConservationScore.fromFiles(protein.structure, protein.conservationPathForChain)
            protein.secondaryData.put(ConservationScore.conservationScoreKey, score)
            protein.secondaryData.put(ConservationScore.conservationLoadedKey, true)
        }
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext protein) {
        Group parentAA = proteinSurfaceAtom.getGroup()
        if (parentAA.getType() != GroupType.AMINOACID) {
            return [0.0] as double[]
        }

        ConservationScore score = (ConservationScore)protein.protein.secondaryData.get(ConservationScore.conservationScoreKey)
        if (score == null) {
            return [0.0] as double[]
        } else {
            double value = score.getScoreForResidue(parentAA.getResidueNumber())
            //double value = rand.nextDouble();
            return [value] as double[]
        }
    }

}
