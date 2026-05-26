package cz.siret.prank.prediction.metrics;

/**
 * Memory-efficient AUC and AUPRC computation directly from sorted
 * {@link PredictedScores}.
 *
 * <p>Both metrics are computed in a single O(N) pass over the
 * descending-score-sorted primitive arrays, using zero extra allocation
 * beyond a few counters.
 *
 * <p>AUC: trapezoidal rule over (FPR, TPR) curve.
 * <br>AUPRC: interpolated using Davis &amp; Goadrich (2006) convention —
 * area under the stepwise precision-recall curve where precision at
 * each threshold is TP/(TP+FP) and recall is TP/P.
 */
public final class CurveMetrics {

    private CurveMetrics() {}

    /**
     * Compute area under the ROC curve (trapezoidal integration).
     *
     * @param preds non-null, non-empty predictions (will be sorted in place)
     * @return AUC in [0, 1], or NaN if degenerate (all same class)
     */
    public static double areaUnderROC(PredictedScores preds) {
        preds.sortDescendingByScore();

        int n = preds.size();
        double[] scores = preds.getScoresArray();
        boolean[] observed = preds.getObservedArray();

        int totalPos = preds.getObservedPositiveCount();
        int totalNeg = n - totalPos;
        if (totalPos == 0 || totalNeg == 0) return Double.NaN;

        double auc = 0.0;
        int tp = 0;
        int fp = 0;
        int prevTp = 0;
        int prevFp = 0;
        double prevScore = Double.POSITIVE_INFINITY;

        for (int i = 0; i < n; i++) {
            double score = scores[i];

            // When the score changes, emit a trapezoid for the previous tied block
            if (score != prevScore && i > 0) {
                auc += trapezoidArea(prevFp, fp, prevTp, tp, totalNeg, totalPos);
                prevTp = tp;
                prevFp = fp;
                prevScore = score;
            }

            if (observed[i]) {
                tp++;
            } else {
                fp++;
            }

            if (i == 0) {
                prevScore = score;
            }
        }
        // Final trapezoid to (1,1)
        auc += trapezoidArea(prevFp, fp, prevTp, tp, totalNeg, totalPos);

        return auc;
    }

    private static double trapezoidArea(int prevFp, int fp, int prevTp, int tp,
                                        int totalNeg, int totalPos) {
        double x1 = (double) prevFp / totalNeg;
        double x2 = (double) fp / totalNeg;
        double y1 = (double) prevTp / totalPos;
        double y2 = (double) tp / totalPos;
        return (x2 - x1) * (y1 + y2) / 2.0;
    }

    /**
     * Compute area under the precision-recall curve.
     *
     * <p>Uses the step-function convention: at each distinct threshold,
     * precision = TP/(TP+FP), recall = TP/P. The area is the sum of
     * rectangular strips: Δrecall × precision_at_threshold.
     *
     * <p>This matches the behavior of Weka's {@code ThresholdCurve.getPRCArea()}
     * which uses trapezoidal interpolation between (recall, precision) points.
     *
     * @param preds non-null, non-empty predictions (will be sorted in place)
     * @return AUPRC in [0, 1], or NaN if degenerate (no positives)
     */
    public static double areaUnderPRC(PredictedScores preds) {
        preds.sortDescendingByScore();

        int n = preds.size();
        double[] scores = preds.getScoresArray();
        boolean[] observed = preds.getObservedArray();

        int totalPos = preds.getObservedPositiveCount();
        if (totalPos == 0) return Double.NaN;

        double auprc = 0.0;
        int tp = 0;
        int fp = 0;
        double prevPrecision = 1.0;
        double prevRecall = 0.0;

        for (int i = 0; i < n; i++) {
            if (observed[i]) {
                tp++;
            } else {
                fp++;
            }

            // Emit a point at every prediction (or at threshold changes for efficiency,
            // but per-prediction is more precise and still O(N))
            double precision = (double) tp / (tp + fp);
            double recall = (double) tp / totalPos;

            // Trapezoidal interpolation between consecutive PR points
            auprc += (recall - prevRecall) * (precision + prevPrecision) / 2.0;

            prevPrecision = precision;
            prevRecall = recall;
        }

        return auprc;
    }
}
