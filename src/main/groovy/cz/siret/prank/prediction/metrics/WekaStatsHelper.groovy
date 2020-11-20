package cz.siret.prank.prediction.metrics

import cz.siret.prank.program.PrankException
import groovy.transform.CompileStatic
import weka.classifiers.evaluation.NominalPrediction
import weka.classifiers.evaluation.Prediction
import weka.classifiers.evaluation.ThresholdCurve
import weka.core.Instances

/**
 * Helps to calculate Weka AUC and AUPRC
 *
 * note: calculation is extremely memory inefficient
 */
@CompileStatic
class WekaStatsHelper {

    List<PPred> preds
    Instances wekaPreds

    /**
     *
     * @param preds
     */
    WekaStatsHelper(List<PPred> preds) {
        if (preds == null || preds.empty) {
            throw new PrankException("Predictions cannot be empty!")
        }

        this.preds = preds
        wekaPreds = new ThresholdCurve().getCurve(toWekaNominalPredictions(preds), 1)
    }

    private ArrayList<Prediction> toWekaNominalPredictions(List<PPred> preds) {
        ArrayList<Prediction> res = new ArrayList<>(preds.size())
        for (PPred p : preds) {
            double actual = p.observed ? 1d : 0d
            double[] distribution = [1-p.score, p.score] as double[]
            res.add(new NominalPrediction(actual, distribution))
        }
        return res
    }

    /**
     * see weka.classifiers.evaluation.Evaluation#areaUnderROC(int)
     */
    double areaUnderROC() {
        return ThresholdCurve.getROCArea(wekaPreds);
    }

    /**
     * see weka.classifiers.evaluation.Evaluation#areaUnderROC(int)
     */
    double areaUnderPRC() {
        return ThresholdCurve.getPRCArea(wekaPreds);
    }

}
