package cz.siret.prank.score.results

import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.stat.Histogram
import groovy.transform.CompileStatic

import java.text.DecimalFormat

/**
 * Binary classifier statistics collector and calculator
 */
@CompileStatic
class ClassifierStats implements Parametrized {

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

    Histograms histograms = new Histograms()
    Stats stats = new Stats()

    boolean collecting = false
    List<Pred> predictions

    ClassifierStats() {
        nclasses = 2
        op = new int[nclasses][nclasses]
        collecting = params.stats_collect_predictions
        if (collecting) {
            predictions = new ArrayList<>()
        }
    }

    void addAll(ClassifierStats add) {
        for (int i=0; i!=nclasses; ++i)
            for (int j=0; j!=nclasses; ++j)
                op[i][j] += add.op[i][j]

        count += add.count

        sumE += add.sumE
        sumEpos += add.sumEpos
        sumEneg += add.sumEneg
        sumSE += add.sumSE
        sumSEpos += add.sumSEpos
        sumSEneg += add.sumSEneg
    }

    /**
     *
     * @param observed   observed class == 1
     * @param predicted   predicted class == 1
     * @param score predicted score from iterval <0,1>
     */
    void addPrediction(boolean observed, boolean predicted, double score, double[] hist) {

        double obsv = observed ? 1 : 0
        double e = Math.abs(obsv-score)
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

        histograms.score.put(score)
        if (observed) {
            histograms.scorePos.put(score)
        } else {
            histograms.scoreNeg.put(score)
        }
        histograms.score0.put(hist[0])
        histograms.score1.put(hist[1])

        if (collecting) {
            predictions.add(new Pred(observed, score))
        }

        op[observed?1:0][predicted?1:0]++
        count++

    }



    double calcMCC(double TP, double FP, double TN, double FN) {
        double n = TP*TN - FP*FN
        double d = (TP+FP)*(TP+FN)*(TN+FP)*(TN+FN)
        d = Math.sqrt(d);
        if (d == 0) {
            d = 1;
        }

        return n / d;
    }

    static String format(double x) {
        return new DecimalFormat("#.####").format(x)
    }

    double fmt(double x) {
        return (double)Math.round(1000*x)/10
    }

    private double rel(double x) {
        return pc((double)x/count)
    }

    private double pc(double x) {
        x *= 100
        return ((double)Math.round(x*10)) / 10
    }

    double div(double a, double b) {
        if (b==0)
            return Double.NaN
        return a / b
    }

//===========================================================================================================//

    static class Pred {
        boolean observed   // true if observed class is 1
        double score       // predicted score for class 1
        Pred(boolean observed, double score) {
            this.observed = observed
            this.score = score
        }
    }

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
    }

    /**
     * flyweight class for 1D statistics 
     */
    class Stats {

        private Advanced advanced = null

        double getTp() { op[1][1] }
        double getFp() { op[0][1] }
        double getTn() { op[0][0] }
        double getFn() { op[1][0] }

        /** Precision = Positive Predictive Value */
        double getP() {
            div tp , (tp + fp)
        }

        /** Recall = Sensitivity = True Positive Rate  */
        double getR() {
            div tp , (tp + fn)
        }

        double getF1() {
            div( (2*(p*r)) , (p+r) )
        }

        double getF2() {
            getFWeighted(2)
        }
        double getF05() {
            getFWeighted(0.5)
        }

        double getMCC() {
            calcMCC(tp, fp, tn, fn)
        }

        /** negative predictive value */
        double getNPV() {
            div tn , (tn + fn)
        }

        /** specificity = true negative rate */
        double getSPC() {
            div tn , (tn + fp)
        }

        /** accuraccy */
        double getACC() {
            div( (tp + tn) , count )
        }

        double getTPX() {
            div tp, tp + fn + fp
        }

        /** false positive rate */
        double getFPR() {
            div fp , (fp + tn)
        }

        /** false negative rate */
        double getFNR() {
            div fn , (tp + fn)
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
            div fp , (tp + fp)
        }

        /** false ommision rate */
        double getFOR() {
            div fn , (fn + tn)
        }

        /** Youden's J statistic = Youden's index */
        double getYJS() {
            r + SPC -1
        }

        /** Discriminant Power ... <1 = poor, >3 = good, fair otherwise */
        double getDPOW() {
            if (r==1 || SPC==1)
                return Double.NaN
            double x = r / (1-r)
            double y = SPC / (1-SPC)
            double c = Math.sqrt(3) / Math.PI

            c * ( Math.log(x) + Math.log(y) )
        }

        double getME()         { div sumE, count        }
        double getMEpos()      { div sumEpos, count     }
        double getMEneg()      { div sumEneg, count     }
        double getMEbalanced() { (MEneg + MEpos) / 2    }

        double getMSE()       { div sumSE, count      }
        double getMSEpos()    { div sumSEpos, count      }
        double getMSEneg()    { div sumSEneg, count      }
        double getMSEbalanced() { (MSEneg + MSEpos) / 2    }


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
            (Map<String, Double>) this.properties
                    .<String, Object>findAll { it.value instanceof Double }
                    .collectEntries { [((String)it.key).toUpperCase(), it.value] }
        }


        Advanced calculateAdvanced() {
            Advanced res = new Advanced()

            if (collecting) {
                WekaStatsHelper wekaHelper = new WekaStatsHelper(predictions)
                res.wekaAUC = wekaHelper.areaUnderROC()
                res.wekaAUPRC = wekaHelper.areaUnderPRC()
            }

            res
        }

        class Advanced {
            double wekaAUC   = Double.NaN
            double wekaAUPRC = Double.NaN
        }
        
    }


    //===========================================================================================================//

    Map<String, Double> getStatsMap() {
        stats.toMap()
    }

    //===========================================================================================================//

    //@CompileStatic
    String toCSV(String classifierDesc) {

        Stats s = stats

        double P = s.p      // precision / positive predictive value
        double R = s.r       // recall / sensitivity / true positive rate

        StringBuilder sb = new StringBuilder()

        stats.with {
            sb << "classifier: ${classifierDesc}\n"
            sb << "\n"
            sb << "n:,$count\n"
            sb << "\n"
            sb << ",TN   , FP, (spc)\n"
            sb << ",FN   , TP, (r)\n"
            sb << ",(npv),(p)\n"
            sb << "\n"
            sb << "pred:  , [0], [1]\n"
            sb << "obs[0] , ${tn}, ${fp}, ${pc(SPC)}\n"
            sb << "obs[1] , ${fn}, ${tp}, ${pc(R)}\n"
            sb << "       , ${pc(NPV)}, ${pc(P)}\n"
            sb << "\n"
            sb << "%:\n"
            sb << ", ${rel(tn)}, ${rel(fp)}\n"
            sb << ", ${rel(fn)}, ${rel(tp)}\n"
            sb << "\n"
            sb << "ACC:, ${format(ACC)}, accuracy\n"
            sb << "\n"
            sb << "P:, ${format(P)}, precision / positive predictive value    ,,TP / (TP + FP)\n"
            sb << "R:, ${format(R)}, recall / sensitivity / true positive rate,,TP / (TP + FN)\n"
            sb << "\n"
            sb << "NPV:, ${format(NPV)}, negative predictive value       ,,TN / (TN + FN)\n"
            sb << "SPC:, ${format(SPC)}, specificity / true negative rate,,TN / (TN + FP)\n"
            sb << "\n"
            sb << "FM:, ${format(f1)}, F-measure\n"
            sb << "MCC:, ${format(MCC)}, Matthews correlation coefficient\n"

            sb << "\n"
            sb << "ME:, ${format(ME)}, Mean error\n"
            sb << "MEpos:, ${format(MEpos)}, ME on positive observations\n"
            sb << "MEneg:, ${format(MEneg)}, Mean error on negative observations\n"
            sb << "MEbal:, ${format(MEbalanced)}, Mean error balanced\n"
            sb << "\n"
            sb << "MSE:, ${format(MSE)}, Mean squared error\n"
            sb << "MSEpos:, ${format(MSEpos)}, MSE on positive observations\n"
            sb << "MSEneg:, ${format(MSEneg)}, MSE on negative observations\n"
            sb << "MSEbal:, ${format(MSEbalanced)}, Mean error balanced\n"
        }

        return sb.toString()
    }

}
