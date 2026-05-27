package cz.siret.prank.domain

import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.geom.Atoms
import cz.siret.prank.prediction.pockets.PrankPocket
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.AtomImpl
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class PredictionFinalizePocketsTest {

    @Test
    void predictModeAssignsRankNewRankAndName() {
        Pocket a = makePocket(10.0d, [makePoint()])
        Pocket b = makePocket(5.0d, [makePoint()])

        Prediction pred = new Prediction(null, [a, b])
        pred.outputPockets = [a, b]
        pred.finalizePredictedPockets()

        assertEquals(1, a.rank)
        assertEquals(1, a.newRank)
        assertEquals("pocket1", a.name)
        assertEquals(2, b.rank)
        assertEquals(2, b.newRank)
        assertEquals("pocket2", b.name)
    }

    @Test
    void rescoreModePreservesRankAndName() {
        Pocket a = makePocket(5.0d, [makePoint()])
        a.rank = 3
        a.name = "pocket.3"
        Pocket b = makePocket(10.0d, [makePoint()])
        b.rank = 1
        b.name = "pocket.1"

        Prediction pred = new Prediction(null, [a, b])
        pred.outputPockets = [b, a]
        pred.finalizeRescoredPockets()

        assertEquals(3, a.rank, "original rank preserved")
        assertEquals("pocket.3", a.name, "original name preserved")
        assertEquals(2, a.newRank, "newRank reflects reordered position")
        assertEquals(1, b.rank)
        assertEquals("pocket.1", b.name)
        assertEquals(1, b.newRank)
    }

    @Test
    void overlappingPointGetsBestNewRank() {
        LabeledPoint shared = makePoint()
        LabeledPoint onlyA = makePoint()
        LabeledPoint onlyB = makePoint()

        Pocket a = makePocket(10.0d, [onlyA, shared])
        Pocket b = makePocket(5.0d, [onlyB, shared])

        Prediction pred = new Prediction(null, [a, b])
        pred.outputPockets = [a, b]
        pred.finalizePredictedPockets()

        assertEquals(1, shared.pocket, "shared point gets best newRank")
        assertEquals(1, onlyA.pocket)
        assertEquals(2, onlyB.pocket)
    }

    @Test
    void resetClearsPreviousAssignment() {
        LabeledPoint pt = makePoint()
        pt.pocket = 99

        Pocket a = makePocket(10.0d, [pt])
        Prediction pred = new Prediction(null, [a])
        pred.outputPockets = [a]
        pred.finalizePredictedPockets()

        assertEquals(1, pt.pocket, "stale pocket value should be reset")
    }

    @Test
    void emptyPocketListIsHandled() {
        Prediction pred = new Prediction(null, [])
        pred.outputPockets = []
        pred.finalizePredictedPockets()
        // no exception
    }

    @Test
    void nullLabeledPointsListIsHandled() {
        Pocket a = makePocket(10.0d, null)
        Prediction pred = new Prediction(null, [a])
        pred.outputPockets = [a]
        pred.finalizePredictedPockets()

        assertEquals(1, a.newRank)
    }

    @Test
    void pocketsAndReorderedPocketsAreIndependent() {
        Pocket a = makePocket(10.0d, [makePoint()])
        Pocket b = makePocket(5.0d, [makePoint()])

        List<Pocket> originalPockets = [a, b]
        Prediction pred = new Prediction(null, originalPockets)
        pred.outputPockets = new ArrayList<>(originalPockets)

        pred.outputPockets.remove(b)
        pred.finalizePredictedPockets()

        assertEquals(2, pred.pockets.size(), "original list unmodified")
        assertEquals(1, pred.outputPockets.size(), "filtered list has 1")
        assertEquals(1, a.newRank)
    }

    @Test
    void filteredPocketPointsResetToZero() {
        LabeledPoint ptA = makePoint()
        LabeledPoint ptB = makePoint()

        Pocket a = makePocket(10.0d, [ptA])
        Pocket b = makePocket(5.0d, [ptB])

        Prediction pred = new Prediction(null, [a, b])
        pred.outputPockets = new ArrayList<>([a, b])
        pred.finalizePredictedPockets()

        assertEquals(1, ptA.pocket)
        assertEquals(2, ptB.pocket)

        // Filter out pocket b and re-finalize
        pred.outputPockets = [a]
        pred.finalizePredictedPockets()

        assertEquals(1, ptA.pocket)
        assertEquals(0, ptB.pocket, "points of filtered-out pocket must be reset to 0")
    }

    // --- Helpers ---

    private static LabeledPoint makePoint() {
        AtomImpl atom = new AtomImpl()
        atom.coords = [0d, 0d, 0d] as double[]
        return new LabeledPoint(atom)
    }

    private static Pocket makePocket(double score, List<LabeledPoint> labeledPoints) {
        AtomImpl centroid = new AtomImpl()
        centroid.coords = [0d, 0d, 0d] as double[]
        Pocket p = new PrankPocket(centroid, score, new Atoms(), labeledPoints ?: [])
        if (labeledPoints == null) {
            p.labeledPoints = null
        }
        p.newScore = score
        return p
    }
}
