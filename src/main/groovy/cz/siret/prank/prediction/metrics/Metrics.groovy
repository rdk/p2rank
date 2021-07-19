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
    }

    Advanced getAdvanced() {
        ensureAdvancedCalculated()
        return advanced
    }

    void ensureAdvancedCalculated() {
        if (this.advanced == null) {
            advanced = new Advanced() // empty
            if (stats.collecting && stats.predictions!=null) {
                if (!stats.predictions.empty)  {
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
    Advanced calculateAdvanced(@Nonnull ArrayList<PPred> predictions) {
        Advanced res = new Advanced()

        res.logLoss = calcLogLoss(stats.predictions)

        // AUC, AUPRC

        WekaStatsHelper wekaHelper = new WekaStatsHelper(predictions)
        res.AUC = wekaHelper.areaUnderROC()
        res.AUPRC = wekaHelper.areaUnderPRC()
        wekaHelper = null // allow gc
        if (res.AUC==Double.NaN) log.error "Calculated AUC is NaN"
        if (res.AUPRC==Double.NaN) log.error "Calculated AUPRC is NaN"
        log.debug "AUC: {}", res.AUC
        log.debug "AUPRC: {}", res.AUPRC

        res.scoreAvg = meanScore(predictions)
        res.positiveScoreAvg = meanScoreObserved(predictions)

        calcScoreStatMoments(res)

        return res
    }

    /**
     * TODO optimize
     */
    private calcScoreStatMoments(Advanced res) {
        try {
            Variance variance = new Variance()
            Skewness skewness = new Skewness()
            Kurtosis kurtosis = new Kurtosis()

            for (PPred pred : stats.predictions) {
                double score = pred.score
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

    private double meanScore(List<PPred> preds) {
        double n = preds.size()
        double sum = 0d

        for (PPred pred : preds) {
            sum += pred.score/n
        }

        return sum
    }

    private double meanScoreObserved(List<PPred> preds) {
        int n = 0
        double sum = 0d

        for (PPred pred : preds) {
            if (pred.observed) {
                sum += pred.score
                n++
            }
        }

        return (double)sum / (double)n
    }

    private double calcLogLoss(List<PPred> preds) {
        final double LOG_LOSS_EPSILON = 0.01
        double n = preds.size()
        double sum = 0d

        for (PPred pred : preds) {
            double pCorrect = pred.observed ? pred.score : 1d-pred.score
            if (pCorrect < LOG_LOSS_EPSILON) {
                pCorrect = LOG_LOSS_EPSILON
            }
            sum -= log(pCorrect)/n
        }

        return sum
    }

}