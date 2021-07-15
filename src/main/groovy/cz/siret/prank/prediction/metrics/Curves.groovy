package cz.siret.prank.prediction.metrics

import groovy.transform.CompileStatic

/**
 *    score:  .9  .9  .8  .8  .5  .4  .3  .3  .1  .0
 *      obs:   1   1   0   1   0   1   0   1   0   0
 *      tps:   1   2   2   3   3   4   4   5   5   5
 *      fps:   0   0   1   1   2   2   3   3   4   5
 */
@CompileStatic
class Curves {

    /**
     * Calculate ROC curve
     *
     * see ranking.py:424 from sklearn
     */
    static Curve roc(ArrayList<PPred> predictions) {
        assert predictions!=null && !predictions.empty

        predictions.sort { -it.score }  // by descending score, sort in place

        int n = predictions.size()
        double[] tps = cumsum(predictions, true)   // true positives
        double[] fps = cumsum(predictions, false)  // false positives

        double[] thresholds = predictions.collect { it.score }.toArray() as double[]

        List<Integer> distinct_threshold_indices = distinctValueIndices(thresholds)
        if (distinct_threshold_indices.last() != n-1)
            distinct_threshold_indices.add(n-1)       // add end of the curve if necessary

        thresholds = select(thresholds, distinct_threshold_indices)
        tps = select(tps, distinct_threshold_indices)
        fps = select(fps, distinct_threshold_indices)

        // TODO? remove suboptimal points

        // TODO? Add an extra threshold position at the beginning if necessary ranking.py:525

        double observed_positives = fps[fps.length-1] // = fp+tn at threshold=0 (tn=0)
        double observed_negatives = tps[tps.length-1] // = fp+tn at threshold=0 (tn=0)

        double[] fpr = divide(fps, observed_positives)
        double[] tpr = divide(tps, observed_negatives)


        return Curve.create(fpr, tpr)
    }

    static List<Integer> distinctValueIndices(double[] sortedVals) {
        assert sortedVals.length > 0

        List<Integer> idxs = new ArrayList<>()

        double val = sortedVals[0]
        idxs.add 0
        for (int i=1; i!=sortedVals.length; ++i) {
            if (sortedVals[i] != val) {
                val = sortedVals[i]
                idxs.add i
            }
        }

        idxs
    }

    static double[] select(double[] from, List<Integer> indices) {
        indices.collect { from[it] }.toArray() as double[]
    }

    static double[] divide(double[] what, double by) {
        double[] res = new double[what.length]
        for (int i=0; i!=what.length; ++i) {
            res[i] = what[i] / by
        }
        res
    }

    private static double[] cumsum(ArrayList<PPred> pred, boolean value) {
        int n = pred.size()
        double[] cumsum = new double[pred.size()]
        int sum = 0
        for (int i=0; i!=n; ++i) {
            if (pred[i].observed == value)
                sum++
            cumsum[i] = sum
        }
        cumsum
    }

}
