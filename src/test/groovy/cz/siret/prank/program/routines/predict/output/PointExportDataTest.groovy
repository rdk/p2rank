package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.collectors.DoubleVector
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.program.routines.predict.output.TableData.ColumnType
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.AtomImpl
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class PointExportDataTest {

    // --- With scores and pocket (predict / rescore) ---

    @Test
    void withScores_headerIncludesScoreAndPocket() {
        def data = PointExportData.create(
            [point(1, 2, 3, 0.8, 0)],
            [vector(0.1, 0.2)],
            ["feat1", "feat2"]
        )

        assertEquals(["x", "y", "z", "score", "pocket", "feat1", "feat2"], data.header)
    }

    @Test
    void withScores_rowIncludesScoreAndPocket() {
        def data = PointExportData.create(
            [point(1, 2, 3, 0.8, 2)],
            [vector(0.1, 0.2)],
            ["feat1", "feat2"]
        )

        double[] row = data.getRow(0)
        assertEquals(7, row.length)
        assertEquals(1.0d, row[0], 1e-9)  // x
        assertEquals(2.0d, row[1], 1e-9)  // y
        assertEquals(3.0d, row[2], 1e-9)  // z
        assertEquals(0.8d, row[3], 1e-9)  // score
        assertEquals(2.0d, row[4], 1e-9)  // pocket
        assertEquals(0.1d, row[5], 1e-9)  // feat1
        assertEquals(0.2d, row[6], 1e-9)  // feat2
    }

    @Test
    void withScores_columnAccess() {
        def data = PointExportData.create(
            [point(1, 2, 3, 0.8, 1), point(4, 5, 6, 0.9, 2)],
            [vector(0.1, 0.2), vector(0.3, 0.4)],
            ["feat1", "feat2"]
        )

        // Score column
        double[] scoreCol = data.getColumn(3)
        assertArrayEquals([0.8d, 0.9d] as double[], scoreCol, 1e-9)

        // Pocket column
        double[] pocketCol = data.getColumn(4)
        assertArrayEquals([1.0d, 2.0d] as double[], pocketCol, 1e-9)

        // First feature column (now index 5)
        double[] feat1Col = data.getColumn(5)
        assertArrayEquals([0.1d, 0.3d] as double[], feat1Col, 1e-9)
    }

    @Test
    void withScores_pocketColumnTypeIsInt() {
        def data = PointExportData.create(
            [point(1, 2, 3, 0.8, 1)],
            [vector(0.1)],
            ["feat1"]
        )

        assertEquals(ColumnType.DOUBLE, data.getColumnType(0))  // x
        assertEquals(ColumnType.DOUBLE, data.getColumnType(1))  // y
        assertEquals(ColumnType.DOUBLE, data.getColumnType(2))  // z
        assertEquals(ColumnType.DOUBLE, data.getColumnType(3))  // score
        assertEquals(ColumnType.INT, data.getColumnType(4))     // pocket
        assertEquals(ColumnType.DOUBLE, data.getColumnType(5))  // feat1
    }

    @Test
    void withScores_pocketValueZeroForUnassigned() {
        def data = PointExportData.create(
            [point(1, 2, 3, 0.5, 0), point(4, 5, 6, 0.5, 3)],
            [vector(0.1), vector(0.2)],
            ["feat1"]
        )

        double[] pocketCol = data.getColumn(4)
        assertArrayEquals([0.0d, 3.0d] as double[], pocketCol, 1e-9)
    }

    // --- Without scores or pocket (export-points standalone) ---

    @Test
    void withoutScores_headerExcludesScoreAndPocket() {
        def data = PointExportData.createWithoutScores(
            [point(1, 2, 3, 0.8, 5)],  // pocket value present on the LP but should be ignored
            [vector(0.1, 0.2)],
            ["feat1", "feat2"]
        )

        assertEquals(["x", "y", "z", "feat1", "feat2"], data.header)
    }

    @Test
    void withoutScores_rowExcludesScoreAndPocket() {
        def data = PointExportData.createWithoutScores(
            [point(1, 2, 3, 0.8, 5)],
            [vector(0.1, 0.2)],
            ["feat1", "feat2"]
        )

        double[] row = data.getRow(0)
        assertEquals(5, row.length)
        assertEquals(1.0d, row[0], 1e-9)  // x
        assertEquals(2.0d, row[1], 1e-9)  // y
        assertEquals(3.0d, row[2], 1e-9)  // z
        assertEquals(0.1d, row[3], 1e-9)  // feat1 (no score/pocket gap)
        assertEquals(0.2d, row[4], 1e-9)  // feat2
    }

    @Test
    void withoutScores_columnAccess() {
        def data = PointExportData.createWithoutScores(
            [point(1, 2, 3, 0.8, 0), point(4, 5, 6, 0.9, 0)],
            [vector(0.1, 0.2), vector(0.3, 0.4)],
            ["feat1", "feat2"]
        )

        // Column 3 is feat1 (no score/pocket)
        double[] feat1Col = data.getColumn(3)
        assertArrayEquals([0.1d, 0.3d] as double[], feat1Col, 1e-9)

        // Column 4 is feat2
        double[] feat2Col = data.getColumn(4)
        assertArrayEquals([0.2d, 0.4d] as double[], feat2Col, 1e-9)
    }

    @Test
    void withoutScores_coordinateColumns() {
        def data = PointExportData.createWithoutScores(
            [point(1, 2, 3, 0, 0), point(4, 5, 6, 0, 0)],
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
            [point(1, 2, 3, 0, 0), point(4, 5, 6, 0, 0)],
            [vector(0.1), vector(0.2)],
            ["feat"]
        )

        assertEquals(2, data.rowCount)
    }

    @Test
    void withoutScores_includeFlagsAreFalse() {
        def data = PointExportData.createWithoutScores(
            [point(1, 2, 3, 0, 0)],
            [vector(0.1)],
            ["feat"]
        )
        assertFalse(data.includeScore)
        assertFalse(data.includePocket)
    }

    @Test
    void withoutScores_allColumnsAreDouble() {
        def data = PointExportData.createWithoutScores(
            [point(1, 2, 3, 0, 0)],
            [vector(0.1)],
            ["feat"]
        )
        for (int c = 0; c < data.header.size(); c++) {
            assertEquals(ColumnType.DOUBLE, data.getColumnType(c), "column $c should be DOUBLE")
        }
    }

    @Test
    void withScores_includeFlagsAreTrue() {
        def data = PointExportData.create(
            [point(1, 2, 3, 0, 0)],
            [vector(0.1)],
            ["feat"]
        )
        assertTrue(data.includeScore)
        assertTrue(data.includePocket)
    }

    @Test
    void builderProducesDataWithScoresAndPocket() {
        def builder = PointExportData.builder(["feat1"])
        builder.add(point(1, 2, 3, 0.5, 4), vector(0.1))
        def data = builder.build()

        assertTrue(data.includeScore)
        assertTrue(data.includePocket)
        assertEquals(["x", "y", "z", "score", "pocket", "feat1"], data.header)
        double[] row = data.getRow(0)
        assertEquals(0.5d, row[3], 1e-9)
        assertEquals(4.0d, row[4], 1e-9)
    }

    // --- Helpers ---

    private static LabeledPoint point(double x, double y, double z, double score, int pocket) {
        def atom = new AtomImpl()
        atom.coords = [x, y, z] as double[]
        def lp = new LabeledPoint(atom)
        lp.score = score
        lp.pocket = pocket
        return lp
    }

    private static FeatureVector vector(double... values) {
        new DoubleVector(values)
    }
}
