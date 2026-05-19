package cz.siret.prank.program.routines.predict.output

import com.carrotsearch.hppc.LongIntHashMap
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid
import cz.siret.prank.program.routines.predict.output.grid.descriptors.PocketGridPointContext
import cz.siret.prank.program.routines.predict.output.grid.descriptors.PocketGridPointDescriptor
import cz.siret.prank.program.routines.predict.output.grid.descriptors.PocketGridPointDescriptorRegistry
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
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

    private static Protein emptyProtein() {
        Protein p = new Protein()
        p.proteinAtoms = new Atoms()
        return p
    }

    @Test
    void descriptorColumnsPrefixedWithDescriptorName() {
        // Multi-column descriptor (volsite, 6 cols) must produce 6 prefixed
        // headers; the prefix rule is documented contract for the export.
        PocketGridRows data = new PocketGridRows(buildTwoPocketGrid(), false,
                emptyProtein(), [] as List<Pocket>, ['volsite'])
        assertEquals(['x', 'y', 'z', 'pocket',
                      'volsite.vsAromatic', 'volsite.vsCation', 'volsite.vsAnion',
                      'volsite.vsHydrophobic', 'volsite.vsAcceptor', 'volsite.vsDonor'],
                data.header)
    }

    @Test
    void getRowAppendsDescriptorValuesAfterBaseColumns() {
        // Empty protein → cutoutSphere is empty → all 6 indicator columns are 0.
        // The point of the test is the row LAYOUT (base 4 then 6 descriptor cols),
        // not the descriptor's numeric semantics — that's covered in
        // VolsiteGridPointDescriptorTest.
        PocketGridRows data = new PocketGridRows(buildTwoPocketGrid(), false,
                emptyProtein(), [] as List<Pocket>, ['volsite'])
        double[] row = data.getRow(0)
        assertEquals(10, row.length)
        // base columns intact
        assertEquals(1.0d, row[0], 0d); assertEquals(0d, row[1], 0d); assertEquals(0d, row[2], 0d)
        assertEquals(1, (int) row[3])
        // descriptor columns all zero (no atoms to classify)
        for (int i = 4; i < row.length; i++) assertEquals(0d, row[i], 0d)
    }

    @Test
    void unknownDescriptorNameThrowsAtConstruction() {
        PocketGrid grid = buildTwoPocketGrid()
        PrankException e = assertThrows(PrankException.class) {
            new PocketGridRows(grid, false, emptyProtein(), [] as List<Pocket>, ['no_such_descriptor'])
        } as PrankException
        // The message must name the typo so the user can fix it.
        assertTrue(e.message.contains('no_such_descriptor'),
                "expected message to mention typo, got: ${e.message}")
    }

    /** Fixture: a 1-column descriptor that exercises the scalar branch of the header rule. */
    @CompileStatic
    private static final class ScalarTestDescriptor implements PocketGridPointDescriptor {
        @Override String name() { return TEST_SCALAR_NAME }
        @Override List<String> columnNames() { return ['ignored'] }
        @Override List<TableData.ColumnType> columnTypes() { return [TableData.ColumnType.DOUBLE] }
        @Override double[] compute(PocketGridPointContext ctx) { return [42.0d] as double[] }
    }
    private static final String TEST_SCALAR_NAME = '__test_scalar_descriptor__'

    @BeforeAll
    static void registerScalarFixture() {
        // Idempotent: register() overwrites by name, so re-running tests in the same JVM
        // is safe. Name is namespaced with underscores so it can't collide with any
        // user-facing CLI name.
        PocketGridPointDescriptorRegistry.register(new ScalarTestDescriptor())
    }

    @AfterAll
    static void unregisterScalarFixture() {
        // Avoid leaking the fixture into the JVM-wide registry — keeps other test
        // classes' assertions on knownNames() deterministic regardless of test order.
        PocketGridPointDescriptorRegistry.unregister(TEST_SCALAR_NAME)
    }

    @Test
    void scalarDescriptorEmitsBareNameWithNoPrefix() {
        // The "{name}.{col}" prefix rule applies ONLY when a descriptor has more than
        // one column. A single-column descriptor's header is exactly name() — sub-name
        // is ignored. None of the shipped descriptors are scalar, so this branch
        // exists for future descriptors and the registered fixture exercises it.
        PocketGridRows data = new PocketGridRows(buildTwoPocketGrid(), false,
                emptyProtein(), [] as List<Pocket>, [TEST_SCALAR_NAME])
        assertEquals(['x', 'y', 'z', 'pocket', TEST_SCALAR_NAME], data.header)
        // The value 42 from compute() must land in the trailing descriptor column.
        double[] row = data.getRow(0)
        assertEquals(5, row.length)
        assertEquals(42.0d, row[4], 0d)
    }

}
