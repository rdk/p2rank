package cz.siret.prank.program.routines.predict.output

import com.carrotsearch.hppc.LongIntHashMap
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class PocketGridRowsTest {

    private static BitSet bits(int... values) {
        BitSet b = new BitSet()
        for (int v : values) b.set(v)
        return b
    }

    /** Build a tiny grid: 3 points (a, b, c), pocket 1 = {a, b}, pocket 2 = {b, c}. */
    private static PocketGrid buildTwoPocketGrid() {
        Atom a = new Point(1.0d, 0d, 0d)
        Atom b = new Point(2.0d, 0d, 0d)
        Atom c = new Point(3.0d, 0d, 0d)
        LongIntHashMap idx = new LongIntHashMap()
        idx.put(PocketGrid.pack(1, 0, 0), 0)
        idx.put(PocketGrid.pack(2, 0, 0), 1)
        idx.put(PocketGrid.pack(3, 0, 0), 2)
        Map<Integer, BitSet> assigned = new LinkedHashMap<>()
        assigned.put(1, bits(0, 1))
        assigned.put(2, bits(1, 2))
        return new PocketGrid(new Atoms([a, b, c]), 1.0d, 0d, 0d, 0d, idx, assigned)
    }

    @Test
    void multiPocketMembershipProducesMultipleRows() {
        // Point b is in both pockets → it appears twice (once per pocket).
        PocketGridRows data = new PocketGridRows(buildTwoPocketGrid(), false)
        assertEquals(4, data.rowCount)  // 2 + 2 assignments
        assertEquals(['x', 'y', 'z', 'pocket'], data.header)
    }

    @Test
    void unassignedIncludedWhenOptedIn() {
        // Add an extra unassigned point.
        Atom a = new Point(1.0d, 0d, 0d)
        Atom unassigned = new Point(5.0d, 0d, 0d)
        LongIntHashMap idx = new LongIntHashMap()
        idx.put(PocketGrid.pack(1, 0, 0), 0)
        idx.put(PocketGrid.pack(5, 0, 0), 1)
        Map<Integer, BitSet> assigned = new LinkedHashMap<>()
        assigned.put(1, bits(0))
        PocketGrid grid = new PocketGrid(new Atoms([a, unassigned]), 1.0d, 0d, 0d, 0d, idx, assigned)

        PocketGridRows included = new PocketGridRows(grid, true)
        assertEquals(2, included.rowCount)  // 1 assigned + 1 unassigned

        PocketGridRows omitted = new PocketGridRows(grid, false)
        assertEquals(1, omitted.rowCount)
    }

    @Test
    void sortOrderIsPocketThenCoords() {
        PocketGridRows data = new PocketGridRows(buildTwoPocketGrid(), false)
        // Expected sort: (pocket=1, x=1,2), then (pocket=2, x=2,3).
        double[] r0 = data.getRow(0); assertEquals(1.0d, r0[0], 0.0d); assertEquals(1, (int) r0[3])
        double[] r1 = data.getRow(1); assertEquals(2.0d, r1[0], 0.0d); assertEquals(1, (int) r1[3])
        double[] r2 = data.getRow(2); assertEquals(2.0d, r2[0], 0.0d); assertEquals(2, (int) r2[3])
        double[] r3 = data.getRow(3); assertEquals(3.0d, r3[0], 0.0d); assertEquals(2, (int) r3[3])
    }

    @Test
    void columnTypes() {
        PocketGridRows data = new PocketGridRows(buildTwoPocketGrid(), false)
        assertEquals(TableData.ColumnType.DOUBLE, data.getColumnType(0))
        assertEquals(TableData.ColumnType.DOUBLE, data.getColumnType(1))
        assertEquals(TableData.ColumnType.DOUBLE, data.getColumnType(2))
        assertEquals(TableData.ColumnType.INT, data.getColumnType(3))
    }

}
