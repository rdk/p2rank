package cz.siret.prank.features.implementation.conservation

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.features.api.ProcessedItemContext
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
class ConservationFeature extends AtomFeatureCalculator {

    @Override
    String getName() {
        "conservation"
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext itemContext) {
        // Check if conservation is already loaded.
        if (!protein.secondaryData.getOrDefault(ConservationScore.CONSERV_LOADED_KEY, false)
                && itemContext.auxData.getOrDefault(ConservationScore.CONSERV_PATH_FUNCTION_KEY, null) != null) {
            // Load conservation score.
            protein.loadConservationScores(itemContext)
        }
        if (protein.secondaryData.get(ConservationScore.CONSERV_SCORE_KEY) == null) {
            log.warn "Conservation not loaded for [{}]", protein.name
        }
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {
        Group parentAA = proteinSurfaceAtom.getGroup()
        if (parentAA.getType() != GroupType.AMINOACID) {
            return [0.0] as double[]
        }

        ConservationScore score = (ConservationScore) context.protein.secondaryData.get(ConservationScore.CONSERV_SCORE_KEY)
        if (score == null) {
            return [0.0] as double[]
        } else {
            double value = score.getScoreForResidue(parentAA.getResidueNumber())
            return [value] as double[]
        }
    }

}
