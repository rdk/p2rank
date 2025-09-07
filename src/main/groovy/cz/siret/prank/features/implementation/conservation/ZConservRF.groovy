package cz.siret.prank.features.implementation.conservation

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.ResidueFeatureCalculationContext
import cz.siret.prank.features.api.ResidueFeatureCalculator
import groovy.transform.CompileStatic

/**
 * Conservation score for residue.
 * (New conservation feature.)
 */
@CompileStatic
class ZConservRF extends ResidueFeatureCalculator {
    
    @Override
    String getName() {
        return 'z-conserv'
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext itemContext) {
        protein.ensureConservationLoaded(itemContext)
    }

    @Override
    double[] calculateForResidue(Residue residue, ResidueFeatureCalculationContext context) {
        double score = getZScoreForResidue(residue, context.protein)

        return [score] as double[]
    }

    static double getZScoreForResidue(Residue residue, Protein protein) {
        ConservationScore score = protein.conservationScore
        if (score == null) {
            return 0d
        } else {
            return score.getZScores().getScoreForResidueSafe(residue.residueNumber)
        } 
    }

}
