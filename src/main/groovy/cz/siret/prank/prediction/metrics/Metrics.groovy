package cz.siret.prank.prediction.metrics

import com.google.common.base.CaseFormat
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.math3.stat.descriptive.moment.Kurtosis
import org.apache.commons.math3.stat.descriptive.moment.Skewness
import org.apache.commons.math3.stat.descriptive.moment.Variance

import javax.annotation.Nonnull

import static java.lang.Double.NaN
import static java.lang.Math.log

/**
 * Calculates binary classification metrics.
 */
@Slf4j
@CompileStatic
class Metrics implements Parametrized {

    ClassifierStats stats
    private Advanced advanced = null

    Metrics(ClassifierStats stats) {
        this.stats = stats
    }

//===========================================================================================================//

    double getTP() { stats.op[1][1] }
    double getFP() { stats.op[0][1] }
    double getTN() { stats.op[0][0] }
    double getFN() { stats.op[1][0] }

    double getCount() { stats.count }

//===========================================================================================================//

    /** Observed Positive */
    double getOP() {
        TP + FN
    }

    /** Observed Negative */
    double getON() {
        FP + TN
    }

    /** Predicted Positive */
    double getPP() {
        TP + FP
    }

    /** Predicted Negative */
    double getPN() {
        TN + FN
    }

    double getOPON_ratio() {
        div OP, ON
    }

    double getPPPN_ratio() {
        div PP, PN
    }

    /** Precision = Positive Predictive Value */
    double getP() {
        div TP , (TP + FP)
    }

    /** Recall = Sensitivity = True Positive Rate  */
    double getR() {
        div TP , (TP + FN)
    }

    /** F-measure */
    double getF1() {
        div( (2*(p*r)) , (p+r) )
    }

    double getF2() {
        getFWeighted(2d)
    }
    double getF05() {
        getFWeighted(0.5d)
    }

    double getMCC() {
        calcMCC(TP, FP, TN, FN)
    }

    /** negative predictive value */
    double getNPV() {
        div TN , (TN + FN)
    }

    /** specificity = true negative rate */
    double getSPC() {
        div TN , (TN + FP)
    }

    /** accuracy */
    double getACC() {
        div( (TP + TN) , count )
    }

    /** balanced accuracy */
    double getBACC() {
        (r + SPC) / 2
    }

    /** TP versus the bad */
    double getTPX() {
        div TP, TP + FN + FP
    }

    /** log TP */
    double getLTP() {
        try {
            -log( TP / (PP * OP) )
        } catch (Exception e) {
            NaN
        }
    }

    /** false positive rate */
    double getFPR() {
        div FP , (FP + TN)
    }

    /** false negative rate */
    double getFNR() {
        div FN , (TP + FN)
    }

    /** positive likelihood ratio */
    double getPLR() {
        div r, FPR
    }

    /** negative likelihood ratio */
    double getNLR() {
        div FNR, SPC
    }

    /** diagnostic odds ratio */
    double getDOR() {
        div PLR, NLR
    }

    /** false discovery rate */
    double getFDR() {
        div FP , (TP + FP)
    }

    /** false omission rate */
    double getFOR() {
        div FN , (FN + TN)
    }

    /** Youden's J statistic = Youden's index = Informedness */
    double getYJS() {
        r + SPC - 1
    }

    /** Markedness */
    double getMRK() {
        p + NPV - 1
    }

    /** Discriminant Power ... <1 = poor, >3 = good, fair otherwise */
    double getDPOW() {
        if (r==1 || SPC==1)
            return NaN
        double x = r / (1-r)
        double y = SPC / (1-SPC)
        double c = Math.sqrt(3) / Math.PI

        c * ( log(x) + log(y) )
    }

    double getME()         { div stats.sumE, count        }
    double getMEpos()      { div stats.sumEpos, count     }
    double getMEneg()      { div stats.sumEneg, count     }
    double getMEbalanced() { (MEneg + MEpos) / 2    }

    double getMSE()       { div stats.sumSE, count      }
    double getMSEpos()    { div stats.sumSEpos, count      }
    double getMSEneg()    { div stats.sumSEneg, count      }
    double getMSEbalanced() { (MSEneg + MSEpos) / 2    }

    /** Uncertainty coefficient, aka Proficiency */
    double getUC() {
        try {
            double L = (OP + ON) * log(OP + ON)
            double LTP = TP * log( TP / (PP * OP) )
            double LFP = FP * log( FP / (PP * ON) )
            double LFN = FN * log( FN / (PN * OP) )
            double LTN = TN * log( TN / (PN * ON) )
            double LP = OP * log( (double)OP / count )
            double LN = ON * log( (double)ON / count )
            double UC = (L + LTP + LFP + LFN + LTN) / (L + LP + LN)
            return UC
        } catch (Exception e) {
            return NaN
        }
    }

    private double getFWeighted(double beta) {
        double betaSqr = beta*beta
        div ( (1+betaSqr)*p*r , r + betaSqr*p  )
    }

//===========================================================================================================//

    double getAUC() {
        getAdvanced().AUC
    }

    double getAUPRC() {
        getAdvanced().AUPRC
    }

    double getLogLoss() {
        getAdvanced().logLoss
    }

    double getScoreAvg() {
        getAdvanced().scoreAvg
    }

    double getScoreVariance() {
        getAdvanced().scoreVariance
    }

    double getscoreSkewness() {
        getAdvanced().scoreSkewness
    }

    double getscoreKurtosis() {
        getAdvanced().scoreKurtosis
    }

    double getPositiveScoreAvg() {
        getAdvanced().positiveScoreAvg
    }

    double getRatP02() {
        getAdvanced().RatP02
    }

    double getRatP04() {
        getAdvanced().RatP04
    }

    double getRatP05() {
        getAdvanced().RatP05
    }

    double getRatP06() {
        getAdvanced().RatP06
    }

    double getRatP08() {
        getAdvanced().RatP08
    }

//===========================================================================================================//

    /**
     * All object properties of type double/Double are included
     */
    SortedMap<String, Double> toMap() {
        SortedMap<String, Double> res = new TreeMap<>() 
        this.properties.findAll { it.value instanceof Double }.each {
            String propertyName = (String)it.key
            String metricName = formatMetricName(propertyName)
            res.put(metricName, (Double)it.value)
        }
        return res
    }

    private String formatMetricName(String propertyName) {
        try {
            if (propertyName.charAt(0).isUpperCase()) {
                return propertyName
            }
            
            return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, propertyName)

        } catch (Exception e) {
            log.debug "failed to format metric name '{}': {}", propertyName, e.message
            return propertyName
        }
    }

//===========================================================================================================//

    static double calcMCC(double TP, double FP, double TN, double FN) {
        double n = TP*TN - FP*FN
        double d = (TP+FP)*(TP+FN)*(TN+FP)*(TN+FN)
        d = Math.sqrt(d)
        if (d == 0d) {
            d = 1d
        }

        return n / d
    }

    double div(double a, double b) {
        if (b==0d)
            return NaN
        return a / b
    }

//===========================================================================================================//

    static class Advanced {
        double AUC = Double.NaN
        double AUPRC = Double.NaN
        double logLoss = Double.NaN

        double scoreAvg = Double.NaN
        double positiveScoreAvg = Double.NaN

        double scoreVariance = Double.NaN
        double scoreSkewness = Double.NaN
        double scoreKurtosis = Double.NaN


        double RatP02 = Double.NaN
        double RatP04 = Double.NaN
        double RatP05 = Double.NaN
        double RatP06 = Double.NaN
        double RatP08 = Double.NaN
    }

    Advanced getAdvanced() {
        ensureAdvancedCalculated()
        return advanced
    }

    void ensureAdvancedCalculated() {
        if (this.advanced == null) {
            advanced = new Advanced() // empty
            if (stats.collecting && stats.predictions!=null) {
                if (!stats.predictions.isEmpty())  {
                    stats.predictions.trimToSize()
                    advanced = calculateAdvanced(stats.predictions)
                } else {
                    log.error "Predictions are empty! Cannot calculate AUC and AUPRC stats."
                }
            }
        }
    }

    /**
     *
     * @param predictions  non-null non-empty
     * @return
     */
    Advanced calculateAdvanced(@Nonnull PredictedScores predictions) {
        Advanced res = new Advanced()

        res.logLoss = calcLogLoss(predictions)

        res.AUC = CurveMetrics.areaUnderROC(predictions)
        res.AUPRC = CurveMetrics.areaUnderPRC(predictions)
        if (Double.isNaN(res.AUC)) log.error "Calculated AUC is NaN"
        if (Double.isNaN(res.AUPRC)) log.error "Calculated AUPRC is NaN"
        log.debug "AUC: {}", res.AUC
        log.debug "AUPRC: {}", res.AUPRC

        res.scoreAvg = meanScore(predictions)
        res.positiveScoreAvg = meanScoreObserved(predictions)

        calcScoreStatMoments(res, predictions)

        double[] recalls = calcMaxRecallForGivenPrecisions(predictions, [0.2d, 0.4d, 0.5d, 0.6d, 0.8d] as double[])
        res.RatP02 = recalls[0]
        res.RatP04 = recalls[1]
        res.RatP05 = recalls[2]
        res.RatP06 = recalls[3]
        res.RatP08 = recalls[4]

        return res
    }

    private calcScoreStatMoments(Advanced res, PredictedScores predictions) {
        try {
            Variance variance = new Variance()
            Skewness skewness = new Skewness()
            Kurtosis kurtosis = new Kurtosis()

            double[] scores = predictions.getScoresArray()
            int n = predictions.size()
            for (int i = 0; i < n; i++) {
                double score = scores[i]
                variance.increment(score)
                skewness.increment(score)
                kurtosis.increment(score)
            }

            res.scoreVariance = variance.getResult()
            res.scoreSkewness = skewness.getResult()
            res.scoreKurtosis = kurtosis.getResult()
        } catch(Exception e) {
            log.warn("Failed to calculate statistical moments for scores", e)
        }
    }

    private double meanScore(PredictedScores preds) {
        double n = preds.size()
        double sum = 0d

        double[] scores = preds.getScoresArray()
        for (int i = 0; i < (int) n; i++) {
            sum += scores[i]/n
        }

        return sum
    }

    private double meanScoreObserved(PredictedScores preds) {
        int n = 0
        double sum = 0d

        double[] scores = preds.getScoresArray()
        boolean[] observed = preds.getObservedArray()
        int size = preds.size()
        for (int i = 0; i < size; i++) {
            if (observed[i]) {
                sum += scores[i]
                n++
            }
        }

        return (double)sum / (double)n
    }

    private double calcLogLoss(PredictedScores preds) {
        final double LOG_LOSS_EPSILON = 0.01
        double n = preds.size()
        double sum = 0d

        double[] scores = preds.getScoresArray()
        boolean[] observed = preds.getObservedArray()
        int size = preds.size()
        for (int i = 0; i < size; i++) {
            double pCorrect = observed[i] ? scores[i] : 1d-scores[i]
            if (pCorrect < LOG_LOSS_EPSILON) {
                pCorrect = LOG_LOSS_EPSILON
            }
            sum -= log(pCorrect)/n
        }

        return sum
    }

    /**
     * Calculates recall values at given precision thresholds for binary classification predictions.
     *
     * @param preds List of predictions with observed outcomes and predicted scores
     * @param precisions Array of precision thresholds sorted in ascending order
     * @return Array of recall values corresponding to each precision threshold
     */
    private static double[] calcMaxRecallForGivenPrecisions(PredictedScores preds, double[] precisions) {
        double[] results = new double[precisions.length]

        if (preds == null || preds.isEmpty()) {
            return results // All zeros
        }

        // Inplace sort predictions by score in descending order (highest score first)
        preds.sortDescendingByScore()

        int totalPositives = preds.getObservedPositiveCount()
        if (totalPositives == 0) {
            return results // All zeros - no positives to recall
        }

        boolean[] observed = preds.getObservedArray()
        int size = preds.size()
        int truePositives = 0
        int processed = 0

        // Single pass through sorted predictions (from highest to lowest score)
        // As we include more predictions, precision will generally trend downward
        // because we're adding lower-scoring predictions which are less likely to be true positives
        for (int idx = 0; idx < size; idx++) {
            processed++
            if (observed[idx]) {
                truePositives++
            }

            double currentPrecision = (double) truePositives / processed
            double currentRecall = (double) truePositives / totalPositives

            // For each precision threshold, record the highest recall achieved
            // when precision is >= threshold
            for (int i = 0; i < precisions.length; i++) {
                if (currentPrecision >= precisions[i]) {
                    // Update result only if this recall is higher than previously recorded
                    results[i] = Math.max(results[i], currentRecall)
                }
            }
        }

        return results
    }

}
