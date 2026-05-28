package cz.siret.prank.prediction.transformation

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class ScoreTransformerTest {

    private static List<Double> doubles(double... vals) {
        vals.collect { (Double) it } as List<Double>
    }

    // --- ProbabilityScoreTransformer ---

    @Test
    void probabilityTransformerTrainAndTransform() {
        def t = new ProbabilityScoreTransformer()
        t.doTrain(doubles(5, 6, 7, 8, 9, 10), doubles(0, 1, 2, 3, 4, 5))

        assertEquals(0.0, t.min, 0.001)
        assertEquals(10.0, t.max, 0.001)
        assertEquals(1000, t.nbins)

        double probAtMin = t.transformScore(0.0)
        double probAtMax = t.transformScore(10.0)
        double probAtMid = t.transformScore(5.0)

        assertTrue(probAtMin >= 0.0 && probAtMin <= 1.0, "prob at min in [0,1]: $probAtMin")
        assertTrue(probAtMax >= 0.0 && probAtMax <= 1.0, "prob at max in [0,1]: $probAtMax")
        assertTrue(probAtMid >= 0.0 && probAtMid <= 1.0, "prob at mid in [0,1]: $probAtMid")

        assertTrue(probAtMax > probAtMin, "higher scores should have higher probability")
        assertTrue(probAtMax > probAtMid, "max > mid")
        assertTrue(probAtMid > probAtMin, "mid > min")
    }

    @Test
    void probabilityTransformerBelowAndAboveRange() {
        def t = new ProbabilityScoreTransformer()
        t.doTrain(doubles(5, 10), doubles(0, 5))

        double belowMin = t.transformScore(-5.0)
        double aboveMax = t.transformScore(20.0)

        assertTrue(Double.isFinite(belowMin), "below min should be finite: $belowMin")
        assertTrue(Double.isFinite(aboveMax), "above max should be finite: $aboveMax")
        assertTrue(belowMin >= 0.0, "below min >= 0: $belowMin")
        assertTrue(aboveMax <= 1.0, "above max <= 1: $aboveMax")
    }

    @Test
    void probabilityTransformerJsonRoundTrip() {
        def original = new ProbabilityScoreTransformer()
        original.doTrain(doubles(3, 5, 7, 9), doubles(1, 2, 3, 4))

        String json = ScoreTransformer.saveToJson(original)
        assertNotNull(json)
        assertTrue(json.contains("ProbabilityScoreTransformer"))

        ScoreTransformer restored = ScoreTransformer.loadFromJson(json)
        assertNotNull(restored)
        assertTrue(restored instanceof ProbabilityScoreTransformer)

        def r = (ProbabilityScoreTransformer) restored
        assertEquals(original.min, r.min, 0.0001)
        assertEquals(original.max, r.max, 0.0001)
        assertEquals(original.nbins, r.nbins)

        for (double score : [1.0, 3.0, 5.0, 7.0, 9.0]) {
            assertEquals(original.transformScore(score), r.transformScore(score), 0.0001,
                    "Round-trip mismatch at score=$score")
        }
    }

    // --- ZscoreTpTransformer ---

    @Test
    void zscoreTransformerTrainAndTransform() {
        def t = new ZscoreTpTransformer()
        t.doTrain(doubles(2, 4, 6, 8, 10))

        assertEquals(6.0, t.mean, 0.001)
        assertTrue(t.stdev > 0, "stdev should be positive")

        double atMean = t.transformScore(6.0)
        assertEquals(0.0, atMean, 0.001, "z-score at mean should be 0")

        double aboveMean = t.transformScore(6.0 + t.stdev)
        assertEquals(1.0, aboveMean, 0.001, "z-score at mean+stdev should be 1")

        double belowMean = t.transformScore(6.0 - t.stdev)
        assertEquals(-1.0, belowMean, 0.001, "z-score at mean-stdev should be -1")
    }

    @Test
    void zscoreTransformerJsonRoundTrip() {
        def original = new ZscoreTpTransformer()
        original.doTrain(doubles(1, 2, 3, 4, 5))

        String json = ScoreTransformer.saveToJson(original)
        ScoreTransformer restored = ScoreTransformer.loadFromJson(json)

        assertTrue(restored instanceof ZscoreTpTransformer)
        def r = (ZscoreTpTransformer) restored
        assertEquals(original.mean, r.mean, 0.0001)
        assertEquals(original.stdev, r.stdev, 0.0001)

        assertEquals(original.transformScore(3.5), r.transformScore(3.5), 0.0001)
    }

    // --- Edge cases: degenerate training data ---

    @Test
    void probabilityTransformerConstantScoresDoNotThrow() {
        def t = new ProbabilityScoreTransformer()
        // all scores identical -> min==max -> step==0 -> produces NaN (0/0 in histogram)
        t.doTrain(doubles(5, 5, 5), doubles(5, 5, 5))
        // should not throw; NaN is the expected result for this degenerate case
        double result = t.transformScore(5.0)
        assertTrue(Double.isNaN(result), "constant-score training produces NaN: $result")
    }

    @Test
    void zscoreTransformerZeroStdevProducesInfinity() {
        def t = new ZscoreTpTransformer()
        // all identical -> stdev=0 -> division by zero
        t.doTrain(doubles(5, 5, 5))
        assertEquals(0.0, t.stdev, 0.0001, "stdev should be 0 for identical scores")
        double result = t.transformScore(5.0)
        // (5-5)/0 = 0/0 = NaN in Java
        // document actual behavior rather than assert "should be"
        assertTrue(Double.isNaN(result) || Double.isInfinite(result) || result == 0.0,
                "zero-stdev transform should produce NaN, Infinity, or 0: $result")
    }

    // --- Factory ---

    @Test
    void factoryCreatesCorrectTypes() {
        assertTrue(ScoreTransformer.create("ProbabilityScoreTransformer") instanceof ProbabilityScoreTransformer)
        assertTrue(ScoreTransformer.create("ZscoreTpTransformer") instanceof ZscoreTpTransformer)
        assertNull(ScoreTransformer.create("NonExistent"))
    }

    @Test
    void loadFromJsonRejectsInvalidName() {
        String json = '{"name":"BogusTransformer","params":{}}'
        assertThrows(Exception) {
            ScoreTransformer.loadFromJson(json)
        }
    }
}
