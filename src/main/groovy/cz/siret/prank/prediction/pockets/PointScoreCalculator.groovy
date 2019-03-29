package cz.siret.prank.prediction.pockets

import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params
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
        hist[1] / (hist[0] + hist[1])
    }

    static boolean applyPointScoreThreshold(double predictedScore) {
        predictedScore >= Params.inst.pred_point_threshold
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
            score = hist[1]  
        } else {
            score = predictedScore(hist)
        }

        score = Math.pow(score, POINT_SCORE_EXP)
        
        return score
    }

    double transformScore(double score) {

        score = Math.pow(score, POINT_SCORE_EXP)
        return score
    }

}
