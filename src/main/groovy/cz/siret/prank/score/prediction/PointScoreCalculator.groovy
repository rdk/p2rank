package cz.siret.prank.score.prediction

import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic

/**
 *
 */
@CompileStatic
class PointScoreCalculator implements Parametrized {

    private final double POINT_SCORE_EXP = params.point_score_pow
    private final boolean USE_ONLY_POSITIVE_SCORE = params.use_only_positive_score

    /**
     * calculate raw classification score form binary classification historgram
     * @param hist length=2
     * @return
     */
    static double predictedScore(double[] hist) {
        hist[1] / hist[0] + hist[1]
    }

    /**
     * calculates ligandability score of the point form binary classification historgram
     *
     * @param hist
     * @return
     */
    double transformedPointScore(double[] hist) {

        double score

        if (USE_ONLY_POSITIVE_SCORE) {
            score = hist[0]
        } else {
            score = predictedScore(hist)
        }

        score = Math.pow(score, POINT_SCORE_EXP)
        
        return score
    }

}
