package cz.siret.prank.features.implementation.conservation

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.GroupType

/**
 * Simple single value Conservation feature that adds conservation from evolution from HSSP database or conservation pipeline to Atom feature vector.
 */
@Slf4j
@CompileStatic
class ConservationFeature extends AtomFeatureCalculator implements Parametrized {

    @Override
    String getName() {
        "conservation"
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext itemContext) {
        protein.ensureConservationLoaded(itemContext)
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {
        Group parentAA = proteinSurfaceAtom.getGroup()
        if (parentAA.getType() != GroupType.AMINOACID) {
            return [0.0] as double[]
        }

        ConservationScore score = context.protein.conservationScore
        if (score == null) {
            return [0.0] as double[]
        } else {
            double value = score.getScoreForResidue(parentAA.getResidueNumber())
            return [value] as double[]
        }
    }

}
