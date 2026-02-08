package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.collectors.DoubleVector
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.features.FeatureVector
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.AtomImpl
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class PointExportDataTest {

    // --- With scores (existing behavior) ---

    @Test
    void withScores_headerIncludesScore() {
        def data = PointExportData.create(
            [point(1, 2, 3, 0.8)],
            [vector(0.1, 0.2)],
            ["feat1", "feat2"]
        )

        assertEquals(["x", "y", "z", "score", "feat1", "feat2"], data.header)
    }

    @Test
    void withScores_rowIncludesScore() {
        def data = PointExportData.create(
            [point(1, 2, 3, 0.8)],
            [vector(0.1, 0.2)],
            ["feat1", "feat2"]
        )

        double[] row = data.getRow(0)
        assertEquals(6, row.length)
        assertEquals(1.0d, row[0], 1e-9)  // x
        assertEquals(2.0d, row[1], 1e-9)  // y
        assertEquals(3.0d, row[2], 1e-9)  // z
        assertEquals(0.8d, row[3], 1e-9)  // score
        assertEquals(0.1d, row[4], 1e-9)  // feat1
        assertEquals(0.2d, row[5], 1e-9)  // feat2
    }

    @Test
    void withScores_columnAccess() {
        def data = PointExportData.create(
            [point(1, 2, 3, 0.8), point(4, 5, 6, 0.9)],
            [vector(0.1, 0.2), vector(0.3, 0.4)],
            ["feat1", "feat2"]
        )

        // Score column
        double[] scoreCol = data.getColumn(3)
        assertArrayEquals([0.8d, 0.9d] as double[], scoreCol, 1e-9)

        // First feature column (index 4)
        double[] feat1Col = data.getColumn(4)
        assertArrayEquals([0.1d, 0.3d] as double[], feat1Col, 1e-9)
    }

    // --- Without scores (export-points behavior) ---

    @Test
    void withoutScores_headerExcludesScore() {
        def data = PointExportData.createWithoutScores(
            [point(1, 2, 3, 0.8)],
            [vector(0.1, 0.2)],
            ["feat1", "feat2"]
        )

        assertEquals(["x", "y", "z", "feat1", "feat2"], data.header)
    }

    @Test
    void withoutScores_rowExcludesScore() {
        def data = PointExportData.createWithoutScores(
            [point(1, 2, 3, 0.8)],
            [vector(0.1, 0.2)],
            ["feat1", "feat2"]
        )

        double[] row = data.getRow(0)
        assertEquals(5, row.length)
        assertEquals(1.0d, row[0], 1e-9)  // x
        assertEquals(2.0d, row[1], 1e-9)  // y
        assertEquals(3.0d, row[2], 1e-9)  // z
        assertEquals(0.1d, row[3], 1e-9)  // feat1 (no score gap)
        assertEquals(0.2d, row[4], 1e-9)  // feat2
    }

    @Test
    void withoutScores_columnAccess() {
        def data = PointExportData.createWithoutScores(
            [point(1, 2, 3, 0.8), point(4, 5, 6, 0.9)],
            [vector(0.1, 0.2), vector(0.3, 0.4)],
            ["feat1", "feat2"]
        )

        // Column 3 is now feat1 (not score)
        double[] feat1Col = data.getColumn(3)
        assertArrayEquals([0.1d, 0.3d] as double[], feat1Col, 1e-9)

        // Column 4 is feat2
        double[] feat2Col = data.getColumn(4)
        assertArrayEquals([0.2d, 0.4d] as double[], feat2Col, 1e-9)
    }

    @Test
    void withoutScores_coordinateColumns() {
        def data = PointExportData.createWithoutScores(
            [point(1, 2, 3, 0), point(4, 5, 6, 0)],
            [vector(0.1), vector(0.2)],
            ["feat"]
        )

        assertArrayEquals([1.0d, 4.0d] as double[], data.getColumn(0), 1e-9)  // x
        assertArrayEquals([2.0d, 5.0d] as double[], data.getColumn(1), 1e-9)  // y
        assertArrayEquals([3.0d, 6.0d] as double[], data.getColumn(2), 1e-9)  // z
    }

    @Test
    void withoutScores_rowCount() {
        def data = PointExportData.createWithoutScores(
            [point(1, 2, 3, 0), point(4, 5, 6, 0)],
            [vector(0.1), vector(0.2)],
            ["feat"]
        )

        assertEquals(2, data.rowCount)
    }

    @Test
    void withoutScores_includeScoreIsFalse() {
        def data = PointExportData.createWithoutScores(
            [point(1, 2, 3, 0)],
            [vector(0.1)],
            ["feat"]
        )
        assertFalse(data.includeScore)
    }

    @Test
    void withScores_includeScoreIsTrue() {
        def data = PointExportData.create(
            [point(1, 2, 3, 0)],
            [vector(0.1)],
            ["feat"]
        )
        assertTrue(data.includeScore)
    }

    @Test
    void builderProducesDataWithScores() {
        def builder = PointExportData.builder(["feat1"])
        builder.add(point(1, 2, 3, 0.5), vector(0.1))
        def data = builder.build()

        assertTrue(data.includeScore)
        assertEquals(["x", "y", "z", "score", "feat1"], data.header)
    }

    // --- Helpers ---

    private static LabeledPoint point(double x, double y, double z, double score) {
        def atom = new AtomImpl()
        atom.coords = [x, y, z] as double[]
        def lp = new LabeledPoint(atom)
        lp.score = score
        return lp
    }

    private static FeatureVector vector(double... values) {
        new DoubleVector(values)
    }
}
