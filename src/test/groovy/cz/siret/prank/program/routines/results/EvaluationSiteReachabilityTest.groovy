package cz.siret.prank.program.routines.results

import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests for the site reachability computation in {@link Evaluation}.
 * Verifies hotPointCount and siteReachabilityScore for LigRow.
 */
@CompileStatic
class EvaluationSiteReachabilityTest {

    private static LabeledPoint scoredPoint(double x, double y, double z, double score) {
        Atom pt = new Point(x, y, z) as Atom
        new LabeledPoint(pt, false, false, score)
    }

    @Test
    void hotPointCountAboveThreshold() {
        double threshold = 0.4d
        int minCluster = 3

        List<LabeledPoint> points = [
            scoredPoint(0, 0, 0, 0.9d),
            scoredPoint(0, 0.5d, 0, 0.5d),
            scoredPoint(0, 1.0d, 0, 0.3d),
            scoredPoint(0, 1.5d, 0, 0.1d),
        ]

        Atoms labeledPoints = new Atoms(points)
        Atoms siteAtoms = new Atoms([new Point(0, 0, 0) as Atom])
        double cutoff = 2.5d

        List<LabeledPoint> nearPoints = labeledPoints.cutoutShell(siteAtoms, cutoff).toList() as List<LabeledPoint>
        int hotCount = (int) nearPoints.count { it.score >= threshold }

        assertEquals(4, nearPoints.size())
        assertEquals(2, hotCount)
        assertEquals(Math.min((double) hotCount / minCluster, 1.0d), 2d / 3d, 1e-10)
    }

    @Test
    void noPointsNearSite() {
        double threshold = 0.4d

        List<LabeledPoint> points = [
            scoredPoint(100, 100, 100, 0.9d),
        ]

        Atoms labeledPoints = new Atoms(points)
        Atoms siteAtoms = new Atoms([new Point(0, 0, 0) as Atom])
        double cutoff = 2.5d

        List<LabeledPoint> nearPoints = labeledPoints.cutoutShell(siteAtoms, cutoff).toList() as List<LabeledPoint>
        int hotCount = (int) nearPoints.count { it.score >= threshold }

        assertEquals(0, nearPoints.size())
        assertEquals(0, hotCount)
    }

    @Test
    void allPointsAboveThreshold() {
        double threshold = 0.4d
        int minCluster = 3

        List<LabeledPoint> points = [
            scoredPoint(0, 0, 0, 0.9d),
            scoredPoint(0.5d, 0, 0, 0.8d),
            scoredPoint(0, 0.5d, 0, 0.7d),
            scoredPoint(0, 0, 0.5d, 0.6d),
            scoredPoint(1.0d, 0, 0, 0.5d),
        ]

        Atoms labeledPoints = new Atoms(points)
        Atoms siteAtoms = new Atoms([new Point(0, 0, 0) as Atom])
        double cutoff = 2.5d

        List<LabeledPoint> nearPoints = labeledPoints.cutoutShell(siteAtoms, cutoff).toList() as List<LabeledPoint>
        int hotCount = (int) nearPoints.count { it.score >= threshold }

        assertEquals(5, hotCount)
        assertEquals(1.0d, Math.min((double) hotCount / minCluster, 1.0d), 1e-10)
    }

    @Test
    void reachabilityClampedToOne() {
        int minCluster = 3
        int hotCount = 10
        double score = Math.min((double) hotCount / minCluster, 1.0d)
        assertEquals(1.0d, score, 1e-10)
    }

    @Test
    void emptyLabeledPoints() {
        Atoms labeledPoints = new Atoms([])
        Atoms siteAtoms = new Atoms([new Point(0, 0, 0) as Atom])
        double cutoff = 2.5d

        List<LabeledPoint> nearPoints = labeledPoints.cutoutShell(siteAtoms, cutoff).toList() as List<LabeledPoint>
        assertEquals(0, nearPoints.size())
    }
}
