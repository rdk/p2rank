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
import org.junit.jupiter.api.BeforeEach
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
        PocketGridRows data = new PocketGridRows(buildTwoPocketGrid(), false, null, null, [] as List<String>)
        assertEquals(4, data.rowCount)  // 2 + 2 assignments
        assertEquals(['x', 'y', 'z', 'pocket'], data.header)
    }

    @Test
    void unassignedIncludedOnlyWhenOptedIn() {
        // Grid has one assigned point (a, pocket 1) and one unassigned point.
        Atom a = new Point(1.0d, 0d, 0d)
        Atom unassigned = new Point(5.0d, 0d, 0d)
        LongIntHashMap idx = new LongIntHashMap()
        idx.put(PocketGrid.pack(1, 0, 0), 0)
        idx.put(PocketGrid.pack(5, 0, 0), 1)
        Map<Integer, BitSet> assigned = new LinkedHashMap<>()
        assigned.put(1, bits(0))
        PocketGrid grid = new PocketGrid(new Atoms([a, unassigned]), 1.0d, 0d, 0d, 0d, idx, assigned)

        // Off (default): only the assigned point is emitted.
        PocketGridRows omitted = new PocketGridRows(grid, false, null, null, [] as List<String>)
        assertEquals(1, omitted.rowCount)
        assertEquals(1, (int) omitted.getRow(0)[3])  // pocket = 1

        // On: the unassigned point is appended last with pocket = 0.
        PocketGridRows included = new PocketGridRows(grid, true, null, null, [] as List<String>)
        assertEquals(2, included.rowCount)  // 1 assigned + 1 unassigned
        double[] assignedRow = included.getRow(0)
        assertEquals(1.0d, assignedRow[0], 0.0d)
        assertEquals(1, (int) assignedRow[3])         // assigned row first, pocket = 1
        double[] unassignedRow = included.getRow(1)
        assertEquals(5.0d, unassignedRow[0], 0.0d)
        assertEquals(0, (int) unassignedRow[3])        // unassigned row last, pocket = 0
    }

    @Test
    void sortOrderIsPocketThenCoords() {
        PocketGridRows data = new PocketGridRows(buildTwoPocketGrid(), false, null, null, [] as List<String>)
        // Expected sort: (pocket=1, x=1,2), then (pocket=2, x=2,3).
        double[] r0 = data.getRow(0); assertEquals(1.0d, r0[0], 0.0d); assertEquals(1, (int) r0[3])
        double[] r1 = data.getRow(1); assertEquals(2.0d, r1[0], 0.0d); assertEquals(1, (int) r1[3])
        double[] r2 = data.getRow(2); assertEquals(2.0d, r2[0], 0.0d); assertEquals(2, (int) r2[3])
        double[] r3 = data.getRow(3); assertEquals(3.0d, r3[0], 0.0d); assertEquals(2, (int) r3[3])
    }

    @Test
    void columnTypes() {
        PocketGridRows data = new PocketGridRows(buildTwoPocketGrid(), false, null, null, [] as List<String>)
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
        @Override void compute(PocketGridPointContext ctx, double[] out, int offset) { out[offset] = 42.0d }
    }
    private static final String TEST_SCALAR_NAME = '__test_scalar_descriptor__'

    /** Counting fixture: increments {@link #calls} per compute, declares pocket-agnostic. */
    @CompileStatic
    private static final class CountingAgnosticDescriptor implements PocketGridPointDescriptor {
        static int calls = 0
        @Override String name() { return TEST_AGNOSTIC_NAME }
        @Override List<String> columnNames() { return ['n'] }
        @Override List<TableData.ColumnType> columnTypes() { return [TableData.ColumnType.DOUBLE] }
        @Override boolean isPocketAgnostic() { return true }
        // Writes the pointIdx — lets us verify the SAME cached result lands in
        // every row for a multi-pocket point (i.e. result is per-point, not per-row).
        @Override void compute(PocketGridPointContext ctx, double[] out, int offset) {
            calls++
            out[offset] = (double) ctx.pointIndex()
        }
    }
    private static final String TEST_AGNOSTIC_NAME = '__test_counting_agnostic__'

    /** Counting fixture: declares NOT pocket-agnostic (the default). Used to confirm the
     *  runner does NOT cache and calls compute() once per (point, pocket) row. */
    @CompileStatic
    private static final class CountingNonAgnosticDescriptor implements PocketGridPointDescriptor {
        static int calls = 0
        @Override String name() { return TEST_NON_AGNOSTIC_NAME }
        @Override List<String> columnNames() { return ['n'] }
        @Override List<TableData.ColumnType> columnTypes() { return [TableData.ColumnType.DOUBLE] }
        // Inherits isPocketAgnostic() = false (the safe default).
        @Override void compute(PocketGridPointContext ctx, double[] out, int offset) {
            calls++
            out[offset] = (double) ctx.pocketRank()
        }
    }
    private static final String TEST_NON_AGNOSTIC_NAME = '__test_counting_non_agnostic__'

    @BeforeAll
    static void registerFixtures() {
        // Idempotent: register() overwrites by name, so re-running tests in the same JVM
        // is safe. Names are namespaced with underscores so they can't collide with any
        // user-facing CLI name.
        PocketGridPointDescriptorRegistry.register(new ScalarTestDescriptor())
        PocketGridPointDescriptorRegistry.register(new CountingAgnosticDescriptor())
        PocketGridPointDescriptorRegistry.register(new CountingNonAgnosticDescriptor())
    }

    @AfterAll
    static void unregisterFixtures() {
        // Avoid leaking fixtures into the JVM-wide registry — keeps other test
        // classes' assertions on knownNames() deterministic regardless of test order.
        PocketGridPointDescriptorRegistry.unregister(TEST_SCALAR_NAME)
        PocketGridPointDescriptorRegistry.unregister(TEST_AGNOSTIC_NAME)
        PocketGridPointDescriptorRegistry.unregister(TEST_NON_AGNOSTIC_NAME)
    }

    @BeforeEach
    void resetCounters() {
        // Robust against a prior test throwing before its inline reset — guarantees
        // each test sees zero counters regardless of execution order.
        CountingAgnosticDescriptor.calls = 0
        CountingNonAgnosticDescriptor.calls = 0
    }

    @Test
    void scalarDescriptorEmitsBareNameWithNoPrefix() {
        // The "{name}.{col}" prefix rule applies ONLY when a descriptor has more than
        // one column. A single-column descriptor's header is exactly name() — sub-name
        // is ignored. None of the registered descriptors are scalar, so this branch
        // exists for future descriptors and the registered fixture exercises it.
        PocketGridRows data = new PocketGridRows(buildTwoPocketGrid(), false,
                emptyProtein(), [] as List<Pocket>, [TEST_SCALAR_NAME])
        assertEquals(['x', 'y', 'z', 'pocket', TEST_SCALAR_NAME], data.header)
        // The value 42 from compute() must land in the trailing descriptor column.
        double[] row = data.getRow(0)
        assertEquals(5, row.length)
        assertEquals(42.0d, row[4], 0d)
    }

    @Test
    void pocketAgnosticDescriptorComputedOncePerPointEvenWhenInMultiplePockets() {
        // buildTwoPocketGrid has point b (pointIdx 1) in BOTH pockets 1 and 2, plus
        // a in pocket 1 only and c in pocket 2 only — three distinct points across
        // 4 rows. A pocket-agnostic descriptor must compute() exactly 3 times, NOT 4.
        PocketGridRows data = new PocketGridRows(buildTwoPocketGrid(), false,
                emptyProtein(), [] as List<Pocket>, [TEST_AGNOSTIC_NAME])

        assertEquals(4, data.rowCount)
        assertEquals(3, CountingAgnosticDescriptor.calls,
                "pocket-agnostic descriptor must compute once per pointIdx, not per row")

        // The cached result for point b (pointIdx 1) should appear identically in both
        // rows for that point (row 1 = pocket 1 + point b, row 2 = pocket 2 + point b
        // per the documented sort order).
        double[] r1 = data.getRow(1); assertEquals(1.0d, r1[4], 0d)   // pointIdx 1
        double[] r2 = data.getRow(2); assertEquals(1.0d, r2[4], 0d)   // same pointIdx 1
    }

    @Test
    void nonPocketAgnosticDescriptorComputedOncePerRow() {
        // Same grid, but a descriptor with the default isPocketAgnostic() = false
        // gets called once per row (4 times), so different rows for the SAME pointIdx
        // can carry different per-pocket values.
        PocketGridRows data = new PocketGridRows(buildTwoPocketGrid(), false,
                emptyProtein(), [] as List<Pocket>, [TEST_NON_AGNOSTIC_NAME])

        assertEquals(4, data.rowCount)
        assertEquals(4, CountingNonAgnosticDescriptor.calls,
                "non-agnostic descriptor must compute once per (point, pocket) row")

        // Point b (pointIdx 1) appears in rows 1 (pocket 1) and 2 (pocket 2); the
        // values must reflect the per-row pocket rank, not a single cached result.
        double[] r1 = data.getRow(1); assertEquals(1.0d, r1[4], 0d)   // pocket rank 1
        double[] r2 = data.getRow(2); assertEquals(2.0d, r2[4], 0d)   // pocket rank 2
    }

    @Test
    void pocketAgnosticMemoSurvivesK3Overlap() {
        // Higher-K overlap: build a grid where ONE point is shared across 3 pockets.
        // Memo correctness must hold for K > 2 — guards against an off-by-one where
        // the second-pocket hit caches but the third recomputes.
        com.carrotsearch.hppc.LongIntHashMap idx = new com.carrotsearch.hppc.LongIntHashMap()
        Atom p0 = new Point(0d, 0d, 0d)
        idx.put(PocketGrid.pack(0, 0, 0), 0)
        Map<Integer, BitSet> assigned = new LinkedHashMap<>()
        assigned.put(1, bits(0))
        assigned.put(2, bits(0))
        assigned.put(3, bits(0))
        PocketGrid grid = new PocketGrid(new Atoms([p0]), 1.0d, 0d, 0d, 0d, idx, assigned)

        PocketGridRows data = new PocketGridRows(grid, false,
                emptyProtein(), [] as List<Pocket>, [TEST_AGNOSTIC_NAME])

        assertEquals(3, data.rowCount, "one point × three pockets = three rows")
        assertEquals(1, CountingAgnosticDescriptor.calls,
                "single pointIdx in K=3 pockets must compute exactly once")

        // All three rows should carry the same cached value (pointIdx 0).
        for (int i = 0; i < 3; i++) {
            assertEquals(0.0d, data.getRow(i)[4], 0d, "row $i")
        }
    }

}
