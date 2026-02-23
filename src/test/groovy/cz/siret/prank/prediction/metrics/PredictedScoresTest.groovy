package cz.siret.prank.prediction.metrics

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class PredictedScoresTest {

    @Test
    void testAddAndSize() {
        PredictedScores ps = new PredictedScores()
        assertTrue(ps.isEmpty())
        assertEquals(0, ps.size())

        ps.add(true, 0.9d)
        ps.add(false, 0.3d)
        ps.add(true, 0.7d)

        assertEquals(3, ps.size())
        assertFalse(ps.isEmpty())
        assertEquals(2, ps.getObservedPositiveCount())
    }

    @Test
    void testIndexedAccess() {
        PredictedScores ps = new PredictedScores()
        ps.add(true, 0.9d)
        ps.add(false, 0.3d)

        assertEquals(0.9d, ps.getScore(0), 0.0d)
        assertTrue(ps.getObserved(0))
        assertEquals(0.3d, ps.getScore(1), 0.0d)
        assertFalse(ps.getObserved(1))
    }

    @Test
    void testAddAll() {
        PredictedScores a = new PredictedScores()
        a.add(true, 0.9d)
        a.add(false, 0.1d)

        PredictedScores b = new PredictedScores()
        b.add(true, 0.8d)
        b.add(true, 0.7d)

        a.addAll(b)
        assertEquals(4, a.size())
        assertEquals(3, a.getObservedPositiveCount())
        assertEquals(0.8d, a.getScore(2), 0.0d)
        assertEquals(0.7d, a.getScore(3), 0.0d)
    }

    @Test
    void testAddAllEmpty() {
        PredictedScores a = new PredictedScores()
        a.add(true, 0.5d)

        PredictedScores empty = new PredictedScores()
        a.addAll(empty)
        assertEquals(1, a.size())
    }

    @Test
    void testToScoresArray() {
        PredictedScores ps = new PredictedScores()
        ps.add(true, 0.9d)
        ps.add(false, 0.3d)

        double[] copy = ps.toScoresArray()
        assertEquals(2, copy.length)
        assertEquals(0.9d, copy[0], 0.0d)
        assertEquals(0.3d, copy[1], 0.0d)

        // Verify it's a copy (modification doesn't affect original)
        copy[0] = 0.0d
        assertEquals(0.9d, ps.getScore(0), 0.0d)
    }

    @Test
    void testTrimToSize() {
        PredictedScores ps = new PredictedScores(1000)
        ps.add(true, 0.5d)
        ps.add(false, 0.3d)
        ps.trimToSize()

        assertEquals(2, ps.size())
        assertEquals(0.5d, ps.getScore(0), 0.0d)
        assertEquals(0.3d, ps.getScore(1), 0.0d)
    }

    // --- Sort tests ---

    @Test
    void testSortEmpty() {
        PredictedScores ps = new PredictedScores()
        ps.sortDescendingByScore() // should not throw
        assertEquals(0, ps.size())
    }

    @Test
    void testSortSingleElement() {
        PredictedScores ps = new PredictedScores()
        ps.add(true, 0.5d)
        ps.sortDescendingByScore()
        assertEquals(0.5d, ps.getScore(0), 0.0d)
    }

    @Test
    void testSortAlreadySorted() {
        PredictedScores ps = new PredictedScores()
        ps.add(true, 0.9d)
        ps.add(false, 0.7d)
        ps.add(true, 0.3d)
        ps.add(false, 0.1d)

        ps.sortDescendingByScore()

        assertEquals(0.9d, ps.getScore(0), 0.0d)
        assertEquals(0.7d, ps.getScore(1), 0.0d)
        assertEquals(0.3d, ps.getScore(2), 0.0d)
        assertEquals(0.1d, ps.getScore(3), 0.0d)
    }

    @Test
    void testSortReverseOrder() {
        PredictedScores ps = new PredictedScores()
        ps.add(false, 0.1d)
        ps.add(true, 0.3d)
        ps.add(false, 0.7d)
        ps.add(true, 0.9d)

        ps.sortDescendingByScore()

        assertEquals(0.9d, ps.getScore(0), 0.0d)
        assertTrue(ps.getObserved(0))
        assertEquals(0.7d, ps.getScore(1), 0.0d)
        assertFalse(ps.getObserved(1))
        assertEquals(0.3d, ps.getScore(2), 0.0d)
        assertTrue(ps.getObserved(2))
        assertEquals(0.1d, ps.getScore(3), 0.0d)
        assertFalse(ps.getObserved(3))
    }

    @Test
    void testSortStabilityWithTiedScores() {
        // RF produces many tied scores; stability means elements with equal scores
        // preserve their original insertion order
        PredictedScores ps = new PredictedScores()

        // Add elements with tied scores but different observed values
        // to track which is which
        ps.add(true,  0.5d)  // [0] first 0.5
        ps.add(false, 0.5d)  // [1] second 0.5
        ps.add(true,  0.8d)  // [2] first 0.8
        ps.add(false, 0.8d)  // [3] second 0.8
        ps.add(true,  0.3d)  // [4] first 0.3
        ps.add(false, 0.3d)  // [5] second 0.3

        ps.sortDescendingByScore()

        // 0.8 group: first 0.8 (observed=true) should come before second 0.8 (observed=false)
        assertEquals(0.8d, ps.getScore(0), 0.0d)
        assertTrue(ps.getObserved(0))
        assertEquals(0.8d, ps.getScore(1), 0.0d)
        assertFalse(ps.getObserved(1))

        // 0.5 group: first 0.5 (observed=true) should come before second 0.5 (observed=false)
        assertEquals(0.5d, ps.getScore(2), 0.0d)
        assertTrue(ps.getObserved(2))
        assertEquals(0.5d, ps.getScore(3), 0.0d)
        assertFalse(ps.getObserved(3))

        // 0.3 group: first 0.3 (observed=true) should come before second 0.3 (observed=false)
        assertEquals(0.3d, ps.getScore(4), 0.0d)
        assertTrue(ps.getObserved(4))
        assertEquals(0.3d, ps.getScore(5), 0.0d)
        assertFalse(ps.getObserved(5))
    }

    @Test
    void testSortStabilityLargerTiedGroups() {
        // Test stability with a pattern that exercises both insertion sort and merge sort paths
        PredictedScores ps = new PredictedScores()

        // 50 elements with 5 distinct scores, 10 elements each
        // Use alternating observed values as stability markers
        double[] scores = [0.1d, 0.3d, 0.5d, 0.7d, 0.9d] as double[]
        for (int round = 0; round < 10; round++) {
            for (double s : scores) {
                ps.add(round % 2 == 0, s)
            }
        }

        ps.sortDescendingByScore()

        // Verify descending order
        for (int i = 1; i < ps.size(); i++) {
            assertTrue(ps.getScore(i - 1) >= ps.getScore(i),
                "Scores not in descending order at index $i")
        }

        // Verify stability within each tied group:
        // For each score value, the observed pattern should be [true, false, true, false, ...]
        // corresponding to rounds [0, 1, 2, 3, ...]
        int idx = 0
        for (int s = scores.length - 1; s >= 0; s--) {
            for (int round = 0; round < 10; round++) {
                assertEquals(scores[s], ps.getScore(idx), 0.0d)
                assertEquals(round % 2 == 0, ps.getObserved(idx),
                    "Stability violated at index $idx (score=${scores[s]}, round=$round)")
                idx++
            }
        }
    }

    @Test
    void testSortLargeRandomArray() {
        // Compare against a reference sort using PPred-style sorting
        Random rng = new Random(42)
        int n = 10000
        PredictedScores ps = new PredictedScores()

        double[] refScores = new double[n]
        boolean[] refObserved = new boolean[n]
        for (int i = 0; i < n; i++) {
            double score = rng.nextDouble()
            boolean obs = rng.nextBoolean()
            ps.add(obs, score)
            refScores[i] = score
            refObserved[i] = obs
        }

        // Reference: sort indices by score descending (stable)
        Integer[] indices = new Integer[n]
        for (int i = 0; i < n; i++) indices[i] = i
        // Stable sort by score descending
        Arrays.sort(indices, { Integer a, Integer b -> Double.compare(refScores[b], refScores[a]) } as Comparator<Integer>)

        ps.sortDescendingByScore()

        for (int i = 0; i < n; i++) {
            int refIdx = indices[i]
            assertEquals(refScores[refIdx], ps.getScore(i), 0.0d,
                "Score mismatch at position $i")
            assertEquals(refObserved[refIdx], ps.getObserved(i),
                "Observed mismatch at position $i")
        }
    }

    @Test
    void testSortedFlagSkipsRedundantSort() {
        PredictedScores ps = new PredictedScores()
        ps.add(false, 0.3d)
        ps.add(true, 0.9d)

        // First sort should reorder
        ps.sortDescendingByScore()
        assertEquals(0.9d, ps.getScore(0), 0.0d)

        // Second sort should be a no-op (sorted flag)
        ps.sortDescendingByScore()
        assertEquals(0.9d, ps.getScore(0), 0.0d)

        // Adding invalidates sorted flag
        ps.add(true, 0.95d)
        ps.sortDescendingByScore()
        assertEquals(0.95d, ps.getScore(0), 0.0d)
    }

    @Test
    void testSortedFlagInvalidatedByAddAll() {
        PredictedScores a = new PredictedScores()
        a.add(true, 0.9d)
        a.add(false, 0.1d)
        a.sortDescendingByScore()

        PredictedScores b = new PredictedScores()
        b.add(true, 0.5d)

        a.addAll(b)
        // Sort should re-sort after addAll
        a.sortDescendingByScore()
        assertEquals(0.9d, a.getScore(0), 0.0d)
        assertEquals(0.5d, a.getScore(1), 0.0d)
        assertEquals(0.1d, a.getScore(2), 0.0d)
    }

    @Test
    void testGrowthBeyondInitialCapacity() {
        PredictedScores ps = new PredictedScores(2)
        for (int i = 0; i < 100; i++) {
            ps.add(i % 3 == 0, (double) i / 100.0d)
        }
        assertEquals(100, ps.size())
        assertEquals(34, ps.getObservedPositiveCount()) // 0,3,6,...,99 -> 34 values

        ps.sortDescendingByScore()
        // Verify descending order
        for (int i = 1; i < ps.size(); i++) {
            assertTrue(ps.getScore(i - 1) >= ps.getScore(i))
        }
    }

}
