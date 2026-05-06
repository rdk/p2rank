package cz.siret.prank.prediction.pockets.rescorers

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.geom.Atoms
import cz.siret.prank.prediction.pockets.PrankPocket
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.AtomImpl
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 * Tests for PocketRescorer point→pocket labeling semantics.
 */
@CompileStatic
class PocketRescorerTest {

    /**
     * When a LabeledPoint belongs to multiple pockets (extended shells overlap),
     * its lp.pocket value should be set to the BEST (lowest) newRank, not the worst.
     */
    @Test
    void overlappingPointGetsBestRank() {
        LabeledPoint sharedPoint = makePoint(0, 0, 0)
        LabeledPoint onlyA = makePoint(1, 0, 0)
        LabeledPoint onlyB = makePoint(2, 0, 0)

        // pocket A has the better score → newRank=1; pocket B → newRank=2
        Pocket a = makePocket(10.0d, [onlyA, sharedPoint])
        Pocket b = makePocket(5.0d, [onlyB, sharedPoint])

        Prediction prediction = new Prediction(null, [a, b])
        // reorderPockets() does not populate reorderedPockets when params.predictions is true
        // (the production caller does it); pre-set it sorted by newScore desc.
        prediction.reorderedPockets = [a, b]

        new StubRescorer().reorderPockets(prediction, null)

        assertEquals(1, a.newRank)
        assertEquals(2, b.newRank)
        assertEquals(1, onlyA.pocket)
        assertEquals(2, onlyB.pocket)
        assertEquals(1, sharedPoint.pocket, "shared point should keep the BEST (lowest) newRank")
    }

    /**
     * Sanity check: non-overlapping points get the rank of the pocket they're in.
     */
    @Test
    void disjointPointsGetTheirOwnPocketRank() {
        LabeledPoint pa = makePoint(0, 0, 0)
        LabeledPoint pb = makePoint(5, 0, 0)

        Pocket a = makePocket(10.0d, [pa])
        Pocket b = makePocket(5.0d, [pb])

        Prediction prediction = new Prediction(null, [a, b])
        prediction.reorderedPockets = [a, b]

        new StubRescorer().reorderPockets(prediction, null)

        assertEquals(1, pa.pocket)
        assertEquals(2, pb.pocket)
    }

    // --- Helpers ---

    private static LabeledPoint makePoint(double x, double y, double z) {
        AtomImpl atom = new AtomImpl()
        atom.coords = [x, y, z] as double[]
        return new LabeledPoint(atom)
    }

    private static Pocket makePocket(double newScore, List<LabeledPoint> labeledPoints) {
        AtomImpl centroid = new AtomImpl()
        centroid.coords = [0d, 0d, 0d] as double[]
        Pocket p = new PrankPocket(centroid, newScore, new Atoms(), labeledPoints)
        p.newScore = newScore
        return p
    }

    @CompileStatic
    private static class StubRescorer extends PocketRescorer {
        @Override
        void rescorePockets(Prediction prediction, ProcessedItemContext context) {
            // no-op: test sets up newScore and labeledPoints upfront
        }
    }
}
