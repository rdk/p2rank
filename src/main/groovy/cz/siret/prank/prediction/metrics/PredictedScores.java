package cz.siret.prank.prediction.metrics;

import java.util.Arrays;

/**
 * Memory-efficient parallel-array storage for binary classifier point predictions.
 * Replaces ArrayList&lt;PPred&gt; — stores scores and observed labels in primitive arrays,
 * reducing per-prediction cost from ~40 bytes (object) to ~9 bytes (primitives).
 */
public class PredictedScores {

    private static final int DEFAULT_CAPACITY = 1024;
    private static final int INSERTION_SORT_THRESHOLD = 32;

    private double[] scores;
    private boolean[] observed;
    private int size;
    private int observedPositiveCount;
    private boolean sorted;

    public PredictedScores() {
        this(DEFAULT_CAPACITY);
    }

    public PredictedScores(int initialCapacity) {
        scores = new double[initialCapacity];
        observed = new boolean[initialCapacity];
        size = 0;
        observedPositiveCount = 0;
        sorted = true;
    }

    public void add(boolean obs, double score) {
        ensureCapacity(size + 1);
        scores[size] = score;
        observed[size] = obs;
        size++;
        if (obs) {
            observedPositiveCount++;
        }
        sorted = (size <= 1);
    }

    public void addAll(PredictedScores other) {
        if (other.size == 0) return;
        ensureCapacity(size + other.size);
        System.arraycopy(other.scores, 0, scores, size, other.size);
        System.arraycopy(other.observed, 0, observed, size, other.size);
        size += other.size;
        observedPositiveCount += other.observedPositiveCount;
        sorted = false;
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity <= scores.length) return;
        int newCapacity = Math.max(scores.length + scores.length / 2, minCapacity);
        scores = Arrays.copyOf(scores, newCapacity);
        observed = Arrays.copyOf(observed, newCapacity);
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int getObservedPositiveCount() {
        return observedPositiveCount;
    }

    public double getScore(int i) {
        assert i >= 0 && i < size;
        return scores[i];
    }

    public boolean getObserved(int i) {
        assert i >= 0 && i < size;
        return observed[i];
    }

    /**
     * Direct access to backing scores array for hot loops.
     * Callers must use size() as the loop bound.
     */
    public double[] getScoresArray() {
        return scores;
    }

    /**
     * Direct access to backing observed array for hot loops.
     * Callers must use size() as the loop bound.
     */
    public boolean[] getObservedArray() {
        return observed;
    }

    /**
     * Returns a correctly-sized copy of the scores array.
     */
    public double[] toScoresArray() {
        return Arrays.copyOf(scores, size);
    }

    /**
     * Frees wasted capacity. Call before heavy computation passes.
     */
    public void trimToSize() {
        if (scores.length != size) {
            scores = Arrays.copyOf(scores, size);
            observed = Arrays.copyOf(observed, size);
        }
    }

    /**
     * In-place stable sort by score in descending order.
     *
     * Stability is required for reproducibility: Random Forest produces many tied scores
     * (discrete probabilities from averaging tree votes). With tied scores, element order
     * affects intermediate precision/recall values in calcMaxRecallForGivenPrecisions.
     * The original List.sort() used TimSort (stable), so we must match that behavior.
     */
    public void sortDescendingByScore() {
        if (sorted) return;
        if (size > 1) {
            mergeSort(0, size);
        }
        sorted = true;
    }

    // --- Stable merge sort with insertion sort fallback for small segments ---

    private void mergeSort(int lo, int hi) {
        int len = hi - lo;
        if (len <= INSERTION_SORT_THRESHOLD) {
            insertionSortDesc(lo, hi);
            return;
        }

        int mid = lo + (len >>> 1);
        mergeSort(lo, mid);
        mergeSort(mid, hi);

        // Skip merge if already ordered (left's last >= right's first in descending order)
        if (Double.compare(scores[mid - 1], scores[mid]) >= 0) {
            return;
        }

        merge(lo, mid, hi);
    }

    private void merge(int lo, int mid, int hi) {
        int leftLen = mid - lo;

        // Copy left half to temp arrays
        double[] leftScores = new double[leftLen];
        boolean[] leftObserved = new boolean[leftLen];
        System.arraycopy(scores, lo, leftScores, 0, leftLen);
        System.arraycopy(observed, lo, leftObserved, 0, leftLen);

        int i = 0;        // index into left temp
        int j = mid;      // index into right (in-place)
        int k = lo;       // index into output

        while (i < leftLen && j < hi) {
            // Take from left when left >= right (descending, stable: left wins on tie)
            if (Double.compare(leftScores[i], scores[j]) >= 0) {
                scores[k] = leftScores[i];
                observed[k] = leftObserved[i];
                i++;
            } else {
                scores[k] = scores[j];
                observed[k] = observed[j];
                j++;
            }
            k++;
        }

        // Copy remaining left elements (right elements are already in place)
        if (i < leftLen) {
            System.arraycopy(leftScores, i, scores, k, leftLen - i);
            System.arraycopy(leftObserved, i, observed, k, leftLen - i);
        }
    }

    private void insertionSortDesc(int lo, int hi) {
        for (int i = lo + 1; i < hi; i++) {
            double scoreKey = scores[i];
            boolean obsKey = observed[i];
            int j = i - 1;
            // Shift elements that are less than key (descending order)
            // Use strict < for stability: equal elements keep original order
            while (j >= lo && Double.compare(scores[j], scoreKey) < 0) {
                scores[j + 1] = scores[j];
                observed[j + 1] = observed[j];
                j--;
            }
            scores[j + 1] = scoreKey;
            observed[j + 1] = obsKey;
        }
    }

}
