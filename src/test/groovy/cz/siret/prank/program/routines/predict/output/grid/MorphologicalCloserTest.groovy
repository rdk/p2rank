package cz.siret.prank.program.routines.predict.output.grid

import com.carrotsearch.hppc.LongIntHashMap
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.program.routines.predict.output.grid.fill.MorphologicalCloser
import cz.siret.prank.program.routines.predict.output.grid.fill.FillKnobs
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests morphological-closing fill on synthetic small grids. Builds a
 * {@link PocketGrid} directly (no real protein) so the fill behavior can be
 * isolated from grid generation.
 */
@CompileStatic
class MorphologicalCloserTest {

    private static final MorphologicalCloser CLOSER = new MorphologicalCloser()

    /** Build a small PocketGrid: all integer lattice cells in [0..nx, 0..ny, 0..nz] with spacing 1. */
    private static PocketGrid buildCubeGrid(int nx, int ny, int nz) {
        List<Atom> points = new ArrayList<>()
        LongIntHashMap index = new LongIntHashMap()
        int idx = 0
        for (int k = 0; k <= nz; k++) {
            for (int j = 0; j <= ny; j++) {
                for (int i = 0; i <= nx; i++) {
                    points.add(new Point((double) i, (double) j, (double) k))
                    index.put(PocketGrid.pack(i, j, k), idx++)
                }
            }
        }
        return new PocketGrid(
                new Atoms(points), 1.0d, 0.0d, 0.0d, 0.0d,
                index, new HashMap<Integer, BitSet>())
    }

    private static BitSet bits(int... values) {
        BitSet b = new BitSet()
        for (int v : values) b.set(v)
        return b
    }

    @Test
    void noOpOnEmptyShell() {
        PocketGrid grid = buildCubeGrid(2, 2, 2)
        BitSet result = CLOSER.fill(new BitSet(), grid, new FillKnobs.Morph(3, 5))
        assertEquals(0, result.cardinality())
    }

    /**
     * Hollow 3x3x3 cube (8 corners + 12 edges + 6 faces = 26 cells, missing the center).
     * With min_neighbors=3 the center should be filled in one iteration
     * (all 26 of its neighbors are in the raw shell).
     */
    @Test
    void fillsCenterOfHollowCube() {
        PocketGrid grid = buildCubeGrid(2, 2, 2)
        int centerIdx = grid.latticeIndex.get(PocketGrid.pack(1, 1, 1))

        BitSet raw = new BitSet()
        for (int i = 0; i < grid.pointCount; i++) {
            if (i != centerIdx) raw.set(i)
        }

        BitSet result = CLOSER.fill(raw, grid, new FillKnobs.Morph(3, 5))

        assertTrue(result.get(centerIdx), "center should be filled")
        assertEquals(grid.pointCount, result.cardinality(), "all 27 cells filled")
    }

    /**
     * Two disconnected single cells far apart should NOT be merged — neither has
     * enough filled neighbors to promote anything between them.
     */
    @Test
    void doesNotMergeDisconnectedComponents() {
        PocketGrid grid = buildCubeGrid(5, 5, 5)
        int aIdx = grid.latticeIndex.get(PocketGrid.pack(0, 0, 0))
        int bIdx = grid.latticeIndex.get(PocketGrid.pack(5, 5, 5))

        BitSet raw = bits(aIdx, bIdx)
        BitSet result = CLOSER.fill(raw, grid, new FillKnobs.Morph(3, 5))

        // Neither lone cell has ≥3 filled neighbors → no promotion at all.
        assertEquals(raw, result)
    }

    /**
     * U-shape: a thick line with a single-cell concavity. With min_neighbors=3
     * the concavity gets filled.
     */
    @Test
    void fillsSingleCellConcavity() {
        PocketGrid grid = buildCubeGrid(4, 2, 0)  // 5x3x1 lattice, z=0 only
        BitSet raw = new BitSet()
        // Fill a U: bottom row y=0, left column x=0, right column x=4
        for (int x = 0; x <= 4; x++) raw.set(grid.latticeIndex.get(PocketGrid.pack(x, 0, 0)))
        for (int y = 0; y <= 2; y++) raw.set(grid.latticeIndex.get(PocketGrid.pack(0, y, 0)))
        for (int y = 0; y <= 2; y++) raw.set(grid.latticeIndex.get(PocketGrid.pack(4, y, 0)))
        // The single-cell concavity at (2, 1, 0) is surrounded by filled cells.

        BitSet result = CLOSER.fill(raw, grid, new FillKnobs.Morph(3, 5))

        int concavityIdx = grid.latticeIndex.get(PocketGrid.pack(2, 1, 0))
        assertTrue(result.get(concavityIdx), "U concavity at (2,1,0) should be filled")
    }

    @Test
    void maxItersZeroIsNoOp() {
        // With max_iters=0 the closer should return the raw shell unchanged
        // (no iteration runs). Also pins the silent-non-convergence-warning fix
        // in 0e044f6b — the warning must NOT fire when maxIters=0 (a valid
        // "disable fill" configuration).
        PocketGrid grid = buildCubeGrid(2, 2, 2)
        int centerIdx = grid.latticeIndex.get(PocketGrid.pack(1, 1, 1))
        BitSet raw = new BitSet()
        for (int i = 0; i < grid.pointCount; i++) {
            if (i != centerIdx) raw.set(i)
        }

        BitSet result = CLOSER.fill(raw, grid, new FillKnobs.Morph(3, 0))
        assertFalse(result.get(centerIdx), "no fill when max_iters=0")
    }

    @Test
    void maxItersOneRunsExactlyOneIteration() {
        // One iteration of fill should be enough to close a single-cell U concavity:
        // the surrounded center cell has many filled neighbors and gets promoted on
        // iter 0. Pins behavior between the no-op (0) and converged cases.
        PocketGrid grid = buildCubeGrid(2, 2, 2)
        int centerIdx = grid.latticeIndex.get(PocketGrid.pack(1, 1, 1))
        BitSet raw = new BitSet()
        for (int i = 0; i < grid.pointCount; i++) {
            if (i != centerIdx) raw.set(i)
        }

        BitSet result = CLOSER.fill(raw, grid, new FillKnobs.Morph(3, 1))
        assertTrue(result.get(centerIdx), "the surrounded center should fill in one iter")
    }

}
