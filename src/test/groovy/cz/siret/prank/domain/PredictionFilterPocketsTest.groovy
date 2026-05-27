package cz.siret.prank.domain

import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.geom.Atoms
import cz.siret.prank.prediction.pockets.PrankPocket
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.AtomImpl
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class PredictionFilterPocketsTest {

    @Test
    void noFilteringWhenAllDefaults() {
        List<Pocket> pockets = [pocket(10), pocket(5), pocket(1)]
        List<Pocket> result = Prediction.filterPockets(pockets, 0, Double.NaN, Double.NaN, 0)
        assertSame(pockets, result, "should return same list reference when no filtering")
    }

    @Test
    void filterByMinScore() {
        List<Pocket> pockets = [pocket(10), pocket(5), pocket(1)]
        List<Pocket> result = Prediction.filterPockets(pockets, 0, 4.0d, Double.NaN, 0)
        assertEquals(2, result.size())
        assertEquals(10.0d, result[0].newScore)
        assertEquals(5.0d, result[1].newScore)
    }

    @Test
    void filterByMinProbability() {
        Pocket a = pocket(10, 0.9d)
        Pocket b = pocket(5, 0.3d)
        Pocket c = pocket(1, 0.1d)
        List<Pocket> result = Prediction.filterPockets([a, b, c], 0, Double.NaN, 0.5d, 0)
        assertEquals(1, result.size())
        assertSame(a, result[0])
    }

    @Test
    void filterByMaxCount() {
        List<Pocket> pockets = [pocket(10), pocket(5), pocket(1)]
        List<Pocket> result = Prediction.filterPockets(pockets, 2, Double.NaN, Double.NaN, 0)
        assertEquals(2, result.size())
        assertEquals(10.0d, result[0].newScore)
        assertEquals(5.0d, result[1].newScore)
    }

    @Test
    void minCountOverridesScoreFilter() {
        List<Pocket> pockets = [pocket(10), pocket(5), pocket(1)]
        List<Pocket> result = Prediction.filterPockets(pockets, 0, 20.0d, Double.NaN, 2)
        assertEquals(2, result.size(), "min_pockets should restore top 2 even though all fail score filter")
        assertEquals(10.0d, result[0].newScore)
        assertEquals(5.0d, result[1].newScore)
    }

    @Test
    void maxCountCapsMinCount() {
        List<Pocket> pockets = [pocket(10), pocket(5), pocket(1)]
        List<Pocket> result = Prediction.filterPockets(pockets, 1, 20.0d, Double.NaN, 3)
        assertEquals(1, result.size(), "max should win over min when conflicting")
    }

    @Test
    void combinedScoreAndMaxCount() {
        List<Pocket> pockets = [pocket(10), pocket(8), pocket(6), pocket(4), pocket(2)]
        List<Pocket> result = Prediction.filterPockets(pockets, 2, 3.0d, Double.NaN, 0)
        assertEquals(2, result.size())
        assertEquals(10.0d, result[0].newScore)
        assertEquals(8.0d, result[1].newScore)
    }

    @Test
    void emptyInputReturnsEmpty() {
        List<Pocket> result = Prediction.filterPockets([], 5, 1.0d, Double.NaN, 3)
        assertTrue(result.isEmpty())
    }

    @Test
    void probaTpZeroPassesWhenProbabilityFilterDisabled() {
        Pocket a = pocket(10, 0.0d)
        // maxPockets=10 forces past early-return so the findAll closure actually runs
        List<Pocket> result = Prediction.filterPockets([a], 10, Double.NaN, Double.NaN, 0)
        assertEquals(1, result.size(), "probaTP=0 should pass when probability filter is NaN (disabled)")
    }

    @Test
    void negativeScorePassesWhenScoreFilterDisabled() {
        Pocket a = pocket(-5.0d)
        // maxPockets=10 forces past early-return so the findAll closure actually runs
        List<Pocket> result = Prediction.filterPockets([a], 10, Double.NaN, Double.NaN, 0)
        assertEquals(1, result.size(), "negative score should pass when score filter is NaN (disabled)")
    }

    @Test
    void allFilteredOutReturnsEmpty() {
        List<Pocket> pockets = [pocket(1), pocket(0.5d)]
        List<Pocket> result = Prediction.filterPockets(pockets, 0, 10.0d, Double.NaN, 0)
        assertTrue(result.isEmpty(), "all below threshold with min_pockets=0 should return empty")
    }

    @Test
    void singlePocketSurvivesAllFilters() {
        Pocket a = pocket(10, 0.8d)
        List<Pocket> result = Prediction.filterPockets([a], 5, 1.0d, 0.5d, 1)
        assertEquals(1, result.size())
        assertSame(a, result[0])
    }

    // --- Helpers ---

    private static Pocket pocket(double score, double probaTP = 0.0d) {
        AtomImpl centroid = new AtomImpl()
        centroid.coords = [0d, 0d, 0d] as double[]
        LabeledPoint lp = new LabeledPoint(centroid)
        Pocket p = new PrankPocket(centroid, score, new Atoms(), [lp])
        p.newScore = score
        p.auxInfo.probaTP = probaTP
        return p
    }
}
