package cz.siret.prank.program.routines.predict.output.descriptors

import com.carrotsearch.hppc.LongIntHashMap
import cz.siret.prank.domain.Pocket
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.routines.predict.output.TableData.ColumnType
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.AtomImpl
import org.biojava.nbio.structure.Element
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests the six shipped descriptors (volume, sphericity, radius_of_gyration,
 * num_residues, num_surface_atoms, num_grid_points) and the registry.
 */
@CompileStatic
class PocketDescriptorsTest {

    private static class TestPocket extends Pocket {}

    private static final double DELTA = 1e-9d

    /** Build a PocketGrid containing exactly {@code points}, spacing 1.0, one pocket holding all of them. */
    private static PocketGrid gridOfPoints(List<Atom> points) {
        LongIntHashMap index = new LongIntHashMap()
        for (int i = 0; i < points.size(); i++) {
            Atom p = points.get(i)
            index.put(PocketGrid.pack((int) p.x, (int) p.y, (int) p.z), i)
        }
        BitSet bs = new BitSet()
        bs.set(0, points.size())
        Map<Integer, BitSet> assigned = new HashMap<>()
        assigned.put(1, bs)
        return new PocketGrid(new Atoms(points), 1.0d, 0d, 0d, 0d, index, assigned)
    }

    private static PocketGridContext ctx(PocketGrid grid, Pocket pocket) {
        return new PocketGridContext(pocket, null, grid, grid.indicesForPocket(pocket.rank))
    }

    private static List<Atom> cube(int n) {
        List<Atom> res = new ArrayList<>(n * n * n)
        for (int k = 0; k < n; k++) {
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < n; i++) {
                    res.add(new Point((double) i, (double) j, (double) k))
                }
            }
        }
        return res
    }

    private static Atom heavyAtomAt(double x, double y, double z) {
        AtomImpl a = new AtomImpl()
        a.element = Element.C
        a.name = "C"
        a.x = x; a.y = y; a.z = z
        return a
    }

    // --- volume ---

    @Test
    void volumeIs8For8UnitCells() {
        PocketGrid grid = gridOfPoints(cube(2))   // 2x2x2 = 8 points, spacing 1.0
        TestPocket p = new TestPocket(); p.rank = 1
        double v = new VolumeDescriptor().compute(ctx(grid, p))
        assertEquals(8.0d, v, DELTA)
    }

    @Test
    void volumeScalesWithSpacing() {
        List<Atom> pts = cube(2)
        LongIntHashMap index = new LongIntHashMap()
        for (int i = 0; i < pts.size(); i++) {
            Atom pt = pts.get(i)
            index.put(PocketGrid.pack((int) pt.x, (int) pt.y, (int) pt.z), i)
        }
        BitSet bs = new BitSet()
        bs.set(0, pts.size())
        Map<Integer, BitSet> assigned = new HashMap<>()
        assigned.put(1, bs)
        PocketGrid grid = new PocketGrid(new Atoms(pts), 0.5d, 0d, 0d, 0d, index, assigned)
        TestPocket p = new TestPocket(); p.rank = 1
        double v = new VolumeDescriptor().compute(ctx(grid, p))
        assertEquals(8 * 0.125d, v, DELTA)  // 8 cells × 0.5³
    }

    // --- sphericity ---

    @Test
    void sphericityCloseToOneForCube() {
        // 5x5x5 cube — radius from centroid to corner ≈ sqrt(3)*2 ≈ 3.46;
        // V_pocket = 125, V_sphere = 4/3·π·3.46³ ≈ 173.5; ratio ≈ 0.72.
        PocketGrid grid = gridOfPoints(cube(5))
        TestPocket p = new TestPocket(); p.rank = 1
        double s = new SphericityDescriptor().compute(ctx(grid, p))
        assertTrue(s > 0.5d, "cube sphericity ${s} too low")
        assertTrue(s <= 1.0d, "sphericity in [0,1]")
    }

    @Test
    void sphericityIsLowForFlatDisc() {
        // 10x10x1 flat slab — radius ≈ sqrt(2)*5 ≈ 7.07; V_pocket = 100,
        // V_sphere = 4/3·π·7.07³ ≈ 1480; ratio ≈ 0.067 — low.
        List<Atom> pts = new ArrayList<>()
        for (int j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {
                pts.add(new Point((double) i, (double) j, 0d))
            }
        }
        PocketGrid grid = gridOfPoints(pts)
        TestPocket p = new TestPocket(); p.rank = 1
        double s = new SphericityDescriptor().compute(ctx(grid, p))
        assertTrue(s < 0.2d, "flat disc sphericity ${s} too high")
    }

    @Test
    void sphericityZeroForEmptyPocket() {
        PocketGrid empty = new PocketGrid(new Atoms(), 1.0d, 0d, 0d, 0d,
                new LongIntHashMap(),
                Collections.<Integer, BitSet> singletonMap(1, new BitSet()))
        TestPocket p = new TestPocket(); p.rank = 1
        double s = new SphericityDescriptor().compute(ctx(empty, p))
        assertEquals(0.0d, s, DELTA)
    }

    @Test
    void sphericityOneForSinglePoint() {
        PocketGrid grid = gridOfPoints([new Point(0d, 0d, 0d) as Atom])
        TestPocket p = new TestPocket(); p.rank = 1
        double s = new SphericityDescriptor().compute(ctx(grid, p))
        assertEquals(1.0d, s, DELTA)
    }

    // --- num_surface_atoms ---

    @Test
    void numSurfaceAtomsReadsPocketField() {
        TestPocket p = new TestPocket()
        p.rank = 1
        p.surfaceAtoms = new Atoms([heavyAtomAt(0d, 0d, 0d), heavyAtomAt(1d, 0d, 0d), heavyAtomAt(2d, 0d, 0d)])
        PocketGrid grid = gridOfPoints([new Point(0d, 0d, 0d) as Atom])
        double n = new NumSurfaceAtomsDescriptor().compute(ctx(grid, p))
        assertEquals(3.0d, n, DELTA)
    }

    // --- num_residues ---

    @Test
    void numResiduesZeroForEmptyOrNullSurfaceAtoms() {
        // Pocket.getResidues() returns Collections.emptyList() when surfaceAtoms is
        // null or empty. Compute should therefore return 0 without throwing.
        TestPocket pEmpty = new TestPocket(); pEmpty.rank = 1; pEmpty.surfaceAtoms = new Atoms()
        TestPocket pNull  = new TestPocket(); pNull.rank = 1   // surfaceAtoms stays null
        PocketGrid grid = gridOfPoints([new Point(0d, 0d, 0d) as Atom])
        NumResiduesDescriptor d = new NumResiduesDescriptor()
        assertEquals(0.0d, d.compute(ctx(grid, pEmpty)), DELTA)
        assertEquals(0.0d, d.compute(ctx(grid, pNull)), DELTA)
    }

    @Test
    void numResiduesIsGridFree() {
        // Contract: NumResiduesDescriptor doesn't read the grid (needsGrid() == false).
        // This is what lets PocketGridOutputs skip the grid build when only num_residues
        // and num_surface_atoms are selected.
        assertFalse(new NumResiduesDescriptor().needsGrid())
        assertFalse(new NumSurfaceAtomsDescriptor().needsGrid())
    }

    @Test
    void gridDerivedDescriptorsAdvertiseTheirNeed() {
        for (String name : ['volume', 'sphericity', 'radius_of_gyration', 'num_grid_points']) {
            assertTrue(PocketDescriptorRegistry.get(name).needsGrid(),
                    "${name} should advertise needsGrid()=true")
        }
    }

    // --- num_grid_points ---

    @Test
    void numGridPointsCountsAssignedCells() {
        PocketGrid grid = gridOfPoints(cube(3))   // 27 points, all assigned to pocket 1
        TestPocket p = new TestPocket(); p.rank = 1
        double n = new NumGridPointsDescriptor().compute(ctx(grid, p))
        assertEquals(27.0d, n, DELTA)
    }

    @Test
    void numGridPointsZeroForEmptyPocket() {
        PocketGrid empty = new PocketGrid(new Atoms(), 1.0d, 0d, 0d, 0d,
                new com.carrotsearch.hppc.LongIntHashMap(),
                Collections.<Integer, BitSet> singletonMap(1, new BitSet()))
        TestPocket p = new TestPocket(); p.rank = 1
        double n = new NumGridPointsDescriptor().compute(ctx(empty, p))
        assertEquals(0.0d, n, DELTA)
    }

    // --- radius_of_gyration ---

    @Test
    void radiusOfGyrationZeroForEmpty() {
        PocketGrid empty = new PocketGrid(new Atoms(), 1.0d, 0d, 0d, 0d,
                new com.carrotsearch.hppc.LongIntHashMap(),
                Collections.<Integer, BitSet> singletonMap(1, new BitSet()))
        TestPocket p = new TestPocket(); p.rank = 1
        double rg = new RadiusOfGyrationDescriptor().compute(ctx(empty, p))
        assertEquals(0.0d, rg, DELTA)
    }

    @Test
    void radiusOfGyrationZeroForSinglePoint() {
        PocketGrid grid = gridOfPoints([new Point(0d, 0d, 0d) as Atom])
        TestPocket p = new TestPocket(); p.rank = 1
        double rg = new RadiusOfGyrationDescriptor().compute(ctx(grid, p))
        assertEquals(0.0d, rg, DELTA)
    }

    /**
     * Two points symmetric around the origin: r_cm = (0,0,0), |r_i| = 1 each →
     * Rg = sqrt((1² + 1²) / 2) = 1.
     */
    @Test
    void radiusOfGyrationOfTwoPointsAtUnitDistance() {
        PocketGrid grid = gridOfPoints([
                new Point(-1d, 0d, 0d) as Atom,
                new Point(+1d, 0d, 0d) as Atom])
        TestPocket p = new TestPocket(); p.rank = 1
        double rg = new RadiusOfGyrationDescriptor().compute(ctx(grid, p))
        assertEquals(1.0d, rg, DELTA)
    }

    /**
     * 3×3×3 cube of unit-spaced points at (0..2)×(0..2)×(0..2), centered at (1,1,1).
     * Per-dim displacements ∈ {-1, 0, +1} so per-dim variance = (1 + 0 + 1) / 3 = 2/3.
     * 3D Rg² = sum across dims = 3 × 2/3 = 2 → Rg = sqrt(2) ≈ 1.4142.
     */
    @Test
    void radiusOfGyrationOfCube() {
        PocketGrid grid = gridOfPoints(cube(3))
        TestPocket p = new TestPocket(); p.rank = 1
        double rg = new RadiusOfGyrationDescriptor().compute(ctx(grid, p))
        assertEquals(Math.sqrt(2d), rg, 1e-6d)
    }

    // --- registry ---

    @Test
    void registryResolvesKnownNames() {
        ['volume', 'sphericity', 'radius_of_gyration',
         'num_residues', 'num_surface_atoms', 'num_grid_points'].each { String name ->
            PocketDescriptor d = PocketDescriptorRegistry.get(name)
            assertNotNull(d)
            assertEquals(name, d.name())
        }
    }

    @Test
    void registryThrowsOnUnknownName() {
        assertThrows(PrankException) {
            PocketDescriptorRegistry.get("not_a_real_descriptor")
        }
    }

    @Test
    void registryListsKnownNames() {
        Set<String> known = PocketDescriptorRegistry.knownNames()
        assertTrue(known.containsAll(
                ['volume', 'sphericity', 'radius_of_gyration',
                 'num_residues', 'num_surface_atoms', 'num_grid_points'] as Set))
    }

    @Test
    void columnTypesAreCorrect() {
        assertEquals(ColumnType.DOUBLE, PocketDescriptorRegistry.get('volume').columnType())
        assertEquals(ColumnType.DOUBLE, PocketDescriptorRegistry.get('sphericity').columnType())
        assertEquals(ColumnType.DOUBLE, PocketDescriptorRegistry.get('radius_of_gyration').columnType())
        assertEquals(ColumnType.INT, PocketDescriptorRegistry.get('num_residues').columnType())
        assertEquals(ColumnType.INT, PocketDescriptorRegistry.get('num_surface_atoms').columnType())
        assertEquals(ColumnType.INT, PocketDescriptorRegistry.get('num_grid_points').columnType())
    }

}
