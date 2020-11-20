package cz.siret.prank.prediction.metrics

import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.text.DecimalFormat

import static cz.siret.prank.utils.Formatter.formatPercent
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

    int[][] op    // confusion matrix [observed][predicted]
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
     * @param score predicted score from interval <0,1>
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
