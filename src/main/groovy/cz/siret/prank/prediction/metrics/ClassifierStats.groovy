package cz.siret.prank.prediction.metrics

import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.text.DecimalFormat

import static cz.siret.prank.utils.Formatter.formatPercent
import static java.lang.Double.NaN
import static java.lang.Math.log

/**
 * Binary classifier statistics collector and calculator
 */
@Slf4j
@CompileStatic
class ClassifierStats implements Parametrized, Writable {

    static final double EPS = 0.01
    static final int HISTOGRAM_BINS = 100

    String name

    int[][] op    // [observed][predicted]
    int count = 0
    int nclasses

    double sumE = 0
    double sumEpos = 0
    double sumEneg = 0
    double sumSE = 0
    double sumSEpos = 0
    double sumSEneg = 0
    double sumLogLoss = 0

    Histograms histograms = new Histograms()

    /**
     * Flyweight 1D metrics accessor
     */
    Metrics metrics = new Metrics()

    boolean collecting = false      // if true collect individual predictions
    ArrayList<PPred> predictions

    ClassifierStats() {
        nclasses = 2
        op = new int[nclasses][nclasses]
        collecting = params.stats_collect_predictions
        if (collecting) {
            predictions = new ArrayList<>()
        }
    }

    void addAll(ClassifierStats other) {
        for (int i=0; i!=nclasses; ++i)
            for (int j=0; j!=nclasses; ++j)
                op[i][j] += other.op[i][j]

        count += other.count

        sumE += other.sumE
        sumEpos += other.sumEpos
        sumEneg += other.sumEneg
        sumSE += other.sumSE
        sumSEpos += other.sumSEpos
        sumSEneg += other.sumSEneg

        histograms.add(other.histograms)

        if (predictions!=null && other.predictions!=null) {
            predictions.addAll(other.predictions)
        }
    }

    private static final double[] HIST_0 = [1, 0] as double[]
    private static final double[] HIST_1 = [0, 1] as double[]

    void addPrediction(boolean observed, boolean predicted) {
        double score = predicted ? 1 : 0
        double[] hist = predicted ? HIST_1 : HIST_0
        addPrediction(observed, predicted, score, hist)
    }

    /**
     *
     * @param observed
     * @param predicted
     * @param score  must be from <0,1>
     */
    void addPrediction(boolean observed, boolean predicted, double score) {
        if (score < 0d) score = 0d

        double[] hist = [1d-score, score] as double[]
        addPrediction(observed, predicted, score, hist)
    }

    /**
     *
     * @param observed   observed class == 1
     * @param predicted   predicted class == 1
     * @param score predicted score from iterval <0,1>
     */
    void addPrediction(boolean observed, boolean predicted, double score, double[] hist) {

        double obsv = observed ? 1d : 0d
        double e = Math.abs(obsv - score)
        double se = e*e

        sumE += e
        sumSE += se

        if (observed) {
            sumEpos += e
            sumSEpos += se
        } else {
            sumEneg += e
            sumSEneg += se
        }

        double pCorrect = observed ? score : 1d-score
        if (pCorrect<EPS) {
            pCorrect = EPS
        }
        sumLogLoss -= log(pCorrect)
//        write("sumLogLoss: " + sumLogLoss)

        histograms.score.put(score)
        if (observed) {
            histograms.scorePos.put(score)
        } else {
            histograms.scoreNeg.put(score)
        }
        histograms.score0.put(hist[0])
        histograms.score1.put(hist[1])

        if (collecting) {
            predictions.add(new PPred(observed, score))
        }

        op[observed?1:0][predicted?1:0]++
        count++

    }



    double calcMCC(double TP, double FP, double TN, double FN) {
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

    class Histograms {

        /** scores for all points */
        Histogram score  = new Histogram(0, 1, HISTOGRAM_BINS)
        /** scores for observed negatives */
        Histogram scoreNeg  = new Histogram(0, 1, HISTOGRAM_BINS)
        /** scores for observed positives */
        Histogram scorePos  = new Histogram(0, 1, HISTOGRAM_BINS)

        /** predicted hist[0] for all */
        Histogram score0 = new Histogram(0, 1, HISTOGRAM_BINS)
        /** predicted hist[1] for all */
        Histogram score1 = new Histogram(0, 1, HISTOGRAM_BINS)

        void add(Histograms others) {
            score.add(others.score)
            scoreNeg.add(others.scoreNeg)
            scorePos.add(others.scorePos)
            score0.add(others.score0)
            score1.add(others.score1)
        }
    }

    /**
     * flyweight class for 1D statistics 
     */
    @CompileStatic
    class Metrics {

        private Advanced advanced = null

        double getTP() { op[1][1] }
        double getFP() { op[0][1] }
        double getTN() { op[0][0] }
        double getFN() { op[1][0] }

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

        double getME()         { div sumE, count        }
        double getMEpos()      { div sumEpos, count     }
        double getMEneg()      { div sumEneg, count     }
        double getMEbalanced() { (MEneg + MEpos) / 2    }

        double getMSE()       { div sumSE, count      }
        double getMSEpos()    { div sumSEpos, count      }
        double getMSEneg()    { div sumSEneg, count      }
        double getMSEbalanced() { (MSEneg + MSEpos) / 2    }

        double getLogLoss() {
            div sumLogLoss, count
        }

        /** Uncertainty coefficient, aka Proficiency */
        double getUC() {
            try {
                double L = (OP + ON) * log(OP + ON)
                double LTP = TP * log( TP / (PP * OP) )
                double LFP = FP * log( FP / (PP * ON) )
                double LFN = FN * log( FN / (PN * OP) )
                double LTN = TN * log( TN / (PN * ON) )
                double LP = OP * log( OP / count )
                double LN = ON * log( ON / count )
                double UC = (L + LTP + LFP + LFN + LTN) / (L + LP + LN)
                return UC
            } catch (Exception e) {
                return NaN
            }
        }

        double getAUC() {
            if (advanced==null) advanced = calculateAdvanced()
            advanced.wekaAUC
        }

        double getAUPRC() {
            if (advanced==null) advanced = calculateAdvanced()
            advanced.wekaAUPRC
        }


        private double getFWeighted(double beta) {
            double betaSqr = beta*beta
            div ( (1+betaSqr)*p*r , r + betaSqr*p  )
        }

        Map<String, Double> toMap() {
            Map<String, Double> res = new TreeMap<>() // keep them sorted
            this.properties.findAll { it.value instanceof Double }.each {
                res.put( ((String)it.key).toUpperCase(), (Double)it.value )
            }
            return res
        }


        Advanced calculateAdvanced() {
            Advanced res = new Advanced()

            if (collecting && predictions!=null) {
                if (!predictions.empty)  {
                    WekaStatsHelper wekaHelper = new WekaStatsHelper(predictions)
                    res.wekaAUC = wekaHelper.areaUnderROC()
                    res.wekaAUPRC = wekaHelper.areaUnderPRC()

                    if (res.wekaAUC==Double.NaN) log.error "Calculated AUC is NaN"
                    if (res.wekaAUPRC==Double.NaN) log.error "Calculated AUPRC is NaN"

                    // xxx
                    log.warn "AUC: {}", res.wekaAUC
                    log.warn "AUPRC: {}", res.wekaAUPRC
                } else {
                    log.error "Predictions are empty! Cannot calculate AUC and AUPRC stats."
                }
            }

            res
        }

        class Advanced {
            double wekaAUC   = Double.NaN
            double wekaAUPRC = Double.NaN
        }
        
    }


    //===========================================================================================================//

    Map<String, Double> getMetricsMap() {
        metrics.toMap()
    }

    //===========================================================================================================//

    private static String format(double x) {
        return new DecimalFormat("#.####").format(x)
    }

    private static String relative(double x, int count) {
        return formatPercent((double)x/count)
    }


    //@CompileStatic
    String toCSV(String classifierLabel) {

        Metrics m = metrics

        double P = m.p      // precision / positive predictive value
        double R = m.r       // recall / sensitivity / true positive rate

        StringBuilder sb = new StringBuilder()

        m.with {
            sb << "classifier: ${classifierLabel}\n"
            sb << "\n"
            sb << ",TN   , FP, (spc)\n"
            sb << ",FN   , TP, (r)\n"
            sb << ",(npv),(p)\n"
            sb << "\n"
            sb << "n:,$count\n"
            sb << "pred:  , [0],  [1]\n"
            sb << "obs[0] , ${TN},  ${FP}, ${formatPercent(SPC)}\n"
            sb << "obs[1] , ${FN},  ${TP}, ${formatPercent(R)}\n"
            sb << "       , ${formatPercent(NPV)},  ${formatPercent(P)}\n"
            sb << "\n"
            sb << "%:\n"
            sb << ", ${relative(TN, count as int)}, ${relative(FP, count as int)}\n"
            sb << ", ${relative(FN, count as int)}, ${relative(TP, count as int)}\n"
            sb << "\n"
            sb << "ACC:, ${format(ACC)}, accuracy\n"
            sb << "P:, ${format(P)}, precision / positive predictive value    ,,TP / (TP + FP)\n"
            sb << "R:, ${format(R)}, recall / sensitivity / true positive rate,,TP / (TP + FN)\n"
            sb << "NPV:, ${format(NPV)}, negative predictive value       ,,TN / (TN + FN)\n"
            sb << "SPC:, ${format(SPC)}, specificity / true negative rate,,TN / (TN + FP)\n"
            sb << "\n"
            sb << "TPX:, ${format(TPX)}, TP / (TP + FN + FP)\n"
            sb << "AUPRC:, ${format(AUPRC)}, area under PR curve\n"
            sb << "AUC:, ${format(AUC)}, area under ROC curve\n"
            sb << "F1:, ${format(f1)}, f-measure\n"
            sb << "MCC:, ${format(MCC)}, Matthews correlation coefficient\n"
        }
        
        return sb.toString()
    }

}
