package cz.siret.prank.prediction.pockets

import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.prediction.pockets.rescorers.InstancePredictor
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 *
 */
@Slf4j
@CompileStatic
class PointScoreCalculator implements Parametrized {

    private final double POINT_SCORE_EXP = params.pointScorePow
    private final boolean USE_ONLY_POSITIVE_SCORE = params.use_only_positive_score

    /**
     * calculate raw classification score form binary classification histogram
     * @param hist length=2
     * @return
     */
    static double normalizedScore(double[] hist) {
        //if (Params.inst.use_only_positive_score) {
        //    return hist[1]
        //}

        double res = hist[1] / (hist[0] + hist[1])

        if (res < 0d) {
            res = 0d
            //log.warn("normalizedScore={} hist={}", res, hist)
        }
        
        return res
    }

    static boolean applyPointScoreThreshold(double predictedScore) {
        predictedScore >= Params.inst.pred_point_threshold
    }
    
    /**
     * calculates ligandability score of the point form binary classification histogram
     *
     * @param hist
     * @return
     */
    double transformedPointScore(double[] hist) {

        double score

        if (USE_ONLY_POSITIVE_SCORE) {
            score = hist[1]  
        } else {
            score = normalizedScore(hist)
        }

        return transformScore(score)
    }

    double transformScore(double score) {
        if (score < 0d) {
            score = 0d
        }
        score = Math.pow(score, POINT_SCORE_EXP)
        return score
    }

//===========================================================================================================//

    /**
     * assign score and transformedScore to point (ugly way to keep legacy param use_only_positive_score)
     */
    void scorePoint(LabeledPoint point, FeatureVector vector, InstancePredictor predictor) {
        // score and transformed score (ugly way to keep legacy param use_only_positive_score)
        double score
        double transformedScore
        if (USE_ONLY_POSITIVE_SCORE) { // legacy way via histogram
            double[] hist = predictor.getDistributionForPoint(vector)
            score = normalizedScore(hist)
            transformedScore = transformedPointScore(hist)
        } else {
            score = predictor.predictPositive(vector)
            transformedScore = transformScore(score)
        }

        point.score = score
        point.transformedScore = transformedScore
    }

}
