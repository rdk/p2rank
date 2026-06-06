package cz.siret.prank.program.routines.predict.output.grid

import com.carrotsearch.hppc.LongIntHashMap
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.program.routines.predict.output.grid.fill.ErodeDilateCloser
import cz.siret.prank.program.routines.predict.output.grid.fill.MorphologicalCloser
import cz.siret.prank.program.routines.predict.output.grid.fill.FillKnobs
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests the true dilate-then-erode {@link ErodeDilateCloser} (the default {@code closing}
 * filler) on synthetic lattices, mirroring {@code MorphologicalCloserTest}.
 *
 * <p>Two properties matter for the over-overlap fix:
 * <ol>
 *   <li>it still fills enclosed holes/concavities (so the pocket region stays
 *       solid), and</li>
 *   <li>unlike {@link MorphologicalCloser} it does NOT advance the outer
 *       boundary of a convex region -- that outward dilation is what bled into
 *       neighbouring pockets.</li>
 * </ol>
 */
@CompileStatic
class ErodeDilateCloserTest {

    private static final ErodeDilateCloser CLOSER = new ErodeDilateCloser()

    /** All integer lattice cells in [0..n] per axis, spacing 1. */
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

    private static BitSet solidBlock(PocketGrid grid, int lo, int hi) {
        BitSet b = new BitSet()
        for (int x = lo; x <= hi; x++)
            for (int y = lo; y <= hi; y++)
                for (int z = lo; z <= hi; z++)
                    b.set(grid.latticeIndex.get(PocketGrid.pack(x, y, z)))
        return b
    }

    @Test
    void noOpOnEmptyShell() {
        PocketGrid grid = buildCubeGrid(2, 2, 2)
        assertEquals(0, CLOSER.fill(new BitSet(), grid, FillKnobs.Closing.symmetric(1)).cardinality())
    }

    /** Enclosed 1-cell hole at the center of a hollow 3x3x3 cube is filled. */
    @Test
    void fillsEnclosedHole() {
        PocketGrid grid = buildCubeGrid(2, 2, 2)
        int centerIdx = grid.latticeIndex.get(PocketGrid.pack(1, 1, 1))
        BitSet raw = new BitSet()
        for (int i = 0; i < grid.pointCount; i++) if (i != centerIdx) raw.set(i)

        BitSet result = CLOSER.fill(raw, grid, FillKnobs.Closing.symmetric(1))
        assertTrue(result.get(centerIdx), "enclosed hole should be filled")
    }

    /**
     * Conservative semantics (vs MorphologicalCloser): a WIDE-open notch is NOT
     * filled. The U interior here is 3 cells wide and open at the top, so closing
     * dilates into it then erodes it right back. Only enclosed holes/narrow notches
     * (width &lt;= 2*radius) survive -- this is exactly why closing does not balloon
     * pockets toward their neighbours, where MorphologicalCloser would fill it in.
     */
    @Test
    void doesNotFillWideOpenNotch() {
        PocketGrid grid = buildCubeGrid(4, 2, 0)  // 5x3x1, z=0
        BitSet raw = new BitSet()
        for (int x = 0; x <= 4; x++) raw.set(grid.latticeIndex.get(PocketGrid.pack(x, 0, 0)))
        for (int y = 0; y <= 2; y++) raw.set(grid.latticeIndex.get(PocketGrid.pack(0, y, 0)))
        for (int y = 0; y <= 2; y++) raw.set(grid.latticeIndex.get(PocketGrid.pack(4, y, 0)))

        BitSet result = CLOSER.fill(raw, grid, FillKnobs.Closing.symmetric(1))
        int notchIdx = grid.latticeIndex.get(PocketGrid.pack(2, 1, 0))
        assertFalse(result.get(notchIdx), "wide-open notch at (2,1,0) must stay empty")
        // morph_closing, by contrast, fills it (aggressive dilation)
        assertTrue(new MorphologicalCloser().fill(raw, grid, new FillKnobs.Morph(3, 5)).get(notchIdx),
                "morph_closing does fill the open notch (the difference in behaviour)")
    }

    /**
     * THE FIX. A solid convex 3x3x3 block sits in a roomy 7x7x7 grid. True closing
     * (dilate 1, erode 1) restores it exactly -- the outer boundary does not move.
     * MorphologicalCloser, by contrast, dilates the block outward (a flat face shows
     * 9 filled neighbors, above min_neighbors=4), which is the over-dilation that
     * bleeds into neighbouring pockets.
     */
    @Test
    void preservesConvexBoundaryWhereMorphClosingExpands() {
        PocketGrid grid = buildCubeGrid(6, 6, 6)
        BitSet raw = solidBlock(grid, 2, 4)   // 27 cells, two empty layers of margin all around

        BitSet closed = CLOSER.fill(raw, grid, FillKnobs.Closing.symmetric(1))
        assertEquals(raw, closed, "true closing must preserve a convex block's boundary")

        BitSet morphed = new MorphologicalCloser().fill(raw, grid, new FillKnobs.Morph(4, 10))
        assertTrue(morphed.cardinality() > raw.cardinality(),
                "morph_closing expands the block outward (the over-dilation bug): " +
                "${morphed.cardinality()} vs ${raw.cardinality()}")
    }

    /** Two solid blocks separated by a gap wider than 2*radius are not merged. */
    @Test
    void doesNotMergeBlocksAcrossWideGap() {
        PocketGrid grid = buildCubeGrid(10, 2, 2)
        BitSet raw = new BitSet()
        raw.or(solidBlockX(grid, 0, 1))    // block near x=0..1
        raw.or(solidBlockX(grid, 9, 10))   // block near x=9..10  (gap of ~7 cells)

        BitSet closed = CLOSER.fill(raw, grid, FillKnobs.Closing.symmetric(1))   // radius 1 bridges at most 2 cells
        // the gap column at x=5 must stay empty -> blocks not merged
        for (int y = 0; y <= 2; y++)
            for (int z = 0; z <= 2; z++)
                assertFalse(closed.get(grid.latticeIndex.get(PocketGrid.pack(5, y, z))),
                        "gap cell (5,$y,$z) must stay empty -> no merge")
    }

    private static BitSet solidBlockX(PocketGrid grid, int xlo, int xhi) {
        BitSet b = new BitSet()
        for (int x = xlo; x <= xhi; x++)
            for (int y = 0; y <= 2; y++)
                for (int z = 0; z <= 2; z++)
                    b.set(grid.latticeIndex.get(PocketGrid.pack(x, y, z)))
        return b
    }
}
