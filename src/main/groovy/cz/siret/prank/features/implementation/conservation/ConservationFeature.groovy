package cz.siret.prank.features.implementation.conservation

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.program.PrankException
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

    private ConservationScore getConservationScore(Protein protein) {
        (ConservationScore) protein.secondaryData.get(ConservationScore.CONSERV_SCORE_KEY)
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext itemContext) {
        // Check if conservation is already loaded.
        if (getConservationScore(protein) == null) {
            // Load conservation score.
            protein.loadConservationScores(itemContext)
        }

        if (getConservationScore(protein) == null) {
            String msg = "Failed to load conservation for protein [$protein.name]"
            if (params.fail_fast) {
                throw new PrankException(msg)
            } else {
                log.warn msg
            }
        }
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {
        Group parentAA = proteinSurfaceAtom.getGroup()
        if (parentAA.getType() != GroupType.AMINOACID) {
            return [0.0] as double[]
        }

        ConservationScore score = getConservationScore(context.protein)
        if (score == null) {
            return [0.0] as double[]
        } else {
            double value = score.getScoreForResidue(parentAA.getResidueNumber())
            return [value] as double[]
        }
    }

}
