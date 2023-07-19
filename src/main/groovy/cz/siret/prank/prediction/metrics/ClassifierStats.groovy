package cz.siret.prank.prediction.metrics

import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.text.DecimalFormat

import static cz.siret.prank.utils.Cutils.prefixMapKeys
import static cz.siret.prank.utils.Formatter.formatPercent

/**
 * Binary classifier statistics collector and calculator
 */
@Slf4j
@CompileStatic
class ClassifierStats implements Parametrized, Writable {

    static final int HISTOGRAM_BINS = 100

    long[][] op    // confusion matrix [observed][predicted]
    long count = 0
    int nclasses

    double sumE = 0
    double sumEpos = 0
    double sumEneg = 0
    double sumSE = 0
    double sumSEpos = 0
    double sumSEneg = 0

    Histograms histograms = new Histograms()

    /**
     * Flyweight 1D metrics accessor
     */
    Metrics metrics = new Metrics(this)

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


    void addPrediction(boolean observed, boolean predicted) {
        double score = predicted ? 1d : 0d
        addPrediction(observed, predicted, score)
    }

    /**
     *
     * @param observed   observed class == 1
     * @param predicted   predicted class == 1
     * @param score predicted score from interval <0,1>
     */
    void addPrediction(boolean observed, boolean predicted, double score) {

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

        histograms.score.put(score)
        if (observed) {
            histograms.scorePos.put(score)
        } else {
            histograms.scoreNeg.put(score)
        }

        if (collecting) {
            predictions.add(new PPred(observed, score))
        }

        op[observed?1:0][predicted?1:0]++
        count++

    }

//===========================================================================================================//

    class Histograms {

        /** scores for all points */
        Histogram score  = new Histogram("score",0, 1, HISTOGRAM_BINS)
        /** scores for observed negatives */
        Histogram scoreNeg  = new Histogram("scoreNeg", 0, 1, HISTOGRAM_BINS)
        /** scores for observed positives */
        Histogram scorePos  = new Histogram("scorePos", 0, 1, HISTOGRAM_BINS)

        void add(Histograms others) {
            score.add(others.score)
            scoreNeg.add(others.scoreNeg)
            scorePos.add(others.scorePos)
        }

        List<Histogram> getAllHistograms() {
            [score, scoreNeg, scorePos]
        }
        
    }

    //===========================================================================================================//

    Map<String, Double> getMetricsMap() {
        metrics.toMap()
    }

    Map<String, Double> getMetricsMap(String prefix) {
        return prefixMapKeys(getMetricsMap(), prefix)
    }

    //===========================================================================================================//

    private static String format(double x) {
        return new DecimalFormat("#.####").format(x)
    }

    private static String relative(double x, long count) {
        return formatPercent((double)x/count)
    }

    String toCSV(String classifierLabel) {

        Metrics m = metrics

        double P = m.p      // precision / positive predictive value
        double R = m.r       // recall / sensitivity / true positive rate

        StringBuilder sb = new StringBuilder()

        m.with {
            sb << "Classifier: ${classifierLabel}\n"
            sb << "\n"
            sb << "N:, ${count as long}\n"
            sb << "Positives:, ${(long)OP}\n"
            sb << "Negatives:, ${(long)ON}\n"
            sb << "Ratio p/n:, ${format(OPON_ratio)}\n"
            sb << "\n"
            sb << "CONFUSION MATRIX\n"
            sb << "\n"
            sb << "       ,TN   ,  FP, (SPC)\n"
            sb << "       ,FN   ,  TP, (R)\n"
            sb << "       ,(NPV), (P)\n"
            sb << "\n"
            sb << "Absolute:\n"
            sb << "       , pred[0],  pred[1]\n"
            sb << "obs[0] , ${TN as long},  ${FP as long}, ${formatPercent(SPC)}\n"
            sb << "obs[1] , ${FN as long},  ${TP as long}, ${formatPercent(R)}\n"
            sb << "       , ${formatPercent(NPV)},  ${formatPercent(P)}\n"
            sb << "\n"
            sb << "Relative:\n"
            sb << "       , ${relative(TN, count as long)}, ${relative(FP, count as long)}\n"
            sb << "       , ${relative(FN, count as long)}, ${relative(TP, count as long)}\n"
            sb << "\n"
            sb << "METRICS\n"
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
