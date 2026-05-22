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
 * Tests the registered descriptors (volume, sphericity, radius_of_gyration,
 * num_residues, num_surface_atoms, num_grid_points, principal_moments,
 * pocket_net_charge, pocket_charge_polarity, pocket_dipole_magnitude) and the
 * registry.
 */
@CompileStatic
class PocketDescriptorsTest {

    private static class TestPocket extends Pocket {}

    private static final double DELTA = 1e-9d

    /** Single source of truth for the registry assertions — bump on every new descriptor. */
    private static final List<String> EXPECTED_REGISTERED_NAMES = [
            'volume', 'sphericity', 'radius_of_gyration',
            'num_residues', 'num_surface_atoms', 'num_grid_points',
            'principal_moments',
            'pocket_net_charge', 'pocket_charge_polarity', 'pocket_dipole_magnitude',
    ].asImmutable()

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
        double v = new VolumeDescriptor().compute(ctx(grid, p))[0]
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
        double v = new VolumeDescriptor().compute(ctx(grid, p))[0]
        assertEquals(8 * 0.125d, v, DELTA)  // 8 cells × 0.5³
    }

    // --- sphericity ---

    @Test
    void sphericityCloseToOneForCube() {
        // 5x5x5 cube — radius from centroid to corner ≈ sqrt(3)*2 ≈ 3.46;
        // V_pocket = 125, V_sphere = 4/3·π·3.46³ ≈ 173.5; ratio ≈ 0.72.
        PocketGrid grid = gridOfPoints(cube(5))
        TestPocket p = new TestPocket(); p.rank = 1
        double s = new SphericityDescriptor().compute(ctx(grid, p))[0]
        assertTrue(s > 0.5d, "cube sphericity ${s} too low")
        assertTrue(s <= 1.0d, "sphericity in [0,1]")
    }

    @Test
    void sphericityIsLowForFlatDisc() {
        // 10x10x1 flat slab — centroid at (4.5,4.5,0); max distance ≈ 6.36;
        // V_pocket = 100, V_sphere = 4/3·π·6.36³ ≈ 1078; ratio ≈ 0.093 — low.
        List<Atom> pts = new ArrayList<>()
        for (int j = 0; j < 10; j++) {
            for (int i = 0; i < 10; i++) {
                pts.add(new Point((double) i, (double) j, 0d))
            }
        }
        PocketGrid grid = gridOfPoints(pts)
        TestPocket p = new TestPocket(); p.rank = 1
        double s = new SphericityDescriptor().compute(ctx(grid, p))[0]
        assertTrue(s < 0.2d, "flat disc sphericity ${s} too high")
    }

    @Test
    void sphericityZeroForEmptyPocket() {
        PocketGrid empty = new PocketGrid(new Atoms(), 1.0d, 0d, 0d, 0d,
                new LongIntHashMap(),
                Collections.<Integer, BitSet> singletonMap(1, new BitSet()))
        TestPocket p = new TestPocket(); p.rank = 1
        double s = new SphericityDescriptor().compute(ctx(empty, p))[0]
        assertEquals(0.0d, s, DELTA)
    }

    @Test
    void sphericityZeroForSinglePoint() {
        // Degenerate (n ≤ 1) returns 0.0 — consistent with the other shape
        // descriptors (volume, radius_of_gyration, principal_moments).
        PocketGrid grid = gridOfPoints([new Point(0d, 0d, 0d) as Atom])
        TestPocket p = new TestPocket(); p.rank = 1
        double s = new SphericityDescriptor().compute(ctx(grid, p))[0]
        assertEquals(0.0d, s, DELTA)
    }

    // --- num_surface_atoms ---

    @Test
    void numSurfaceAtomsReadsPocketField() {
        TestPocket p = new TestPocket()
        p.rank = 1
        p.surfaceAtoms = new Atoms([heavyAtomAt(0d, 0d, 0d), heavyAtomAt(1d, 0d, 0d), heavyAtomAt(2d, 0d, 0d)])
        PocketGrid grid = gridOfPoints([new Point(0d, 0d, 0d) as Atom])
        double n = new NumSurfaceAtomsDescriptor().compute(ctx(grid, p))[0]
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
        assertEquals(0.0d, d.compute(ctx(grid, pEmpty))[0], DELTA)
        assertEquals(0.0d, d.compute(ctx(grid, pNull))[0], DELTA)
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
        double n = new NumGridPointsDescriptor().compute(ctx(grid, p))[0]
        assertEquals(27.0d, n, DELTA)
    }

    @Test
    void numGridPointsZeroForEmptyPocket() {
        PocketGrid empty = new PocketGrid(new Atoms(), 1.0d, 0d, 0d, 0d,
                new com.carrotsearch.hppc.LongIntHashMap(),
                Collections.<Integer, BitSet> singletonMap(1, new BitSet()))
        TestPocket p = new TestPocket(); p.rank = 1
        double n = new NumGridPointsDescriptor().compute(ctx(empty, p))[0]
        assertEquals(0.0d, n, DELTA)
    }

    // --- radius_of_gyration ---

    @Test
    void radiusOfGyrationZeroForEmpty() {
        PocketGrid empty = new PocketGrid(new Atoms(), 1.0d, 0d, 0d, 0d,
                new com.carrotsearch.hppc.LongIntHashMap(),
                Collections.<Integer, BitSet> singletonMap(1, new BitSet()))
        TestPocket p = new TestPocket(); p.rank = 1
        double rg = new RadiusOfGyrationDescriptor().compute(ctx(empty, p))[0]
        assertEquals(0.0d, rg, DELTA)
    }

    @Test
    void radiusOfGyrationZeroForSinglePoint() {
        PocketGrid grid = gridOfPoints([new Point(0d, 0d, 0d) as Atom])
        TestPocket p = new TestPocket(); p.rank = 1
        double rg = new RadiusOfGyrationDescriptor().compute(ctx(grid, p))[0]
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
        double rg = new RadiusOfGyrationDescriptor().compute(ctx(grid, p))[0]
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
        double rg = new RadiusOfGyrationDescriptor().compute(ctx(grid, p))[0]
        assertEquals(Math.sqrt(2d), rg, 1e-6d)
    }

    // --- registry ---

    @Test
    void registryResolvesKnownNames() {
        EXPECTED_REGISTERED_NAMES.each { String name ->
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

    /**
     * Round-trip the register/unregister API: register a fixture, see it land in
     * get/knownNames, unregister it, see it gone. Exercises the unregister code
     * path that production callers never touch (it exists for tests + future
     * descriptor plugins).
     */
    @Test
    void registryUnregisterRemovesAddedDescriptor() {
        String fixtureName = "__test_unregister_fixture__"
        PocketDescriptor fixture = new AbstractScalarPocketDescriptor() {
            @Override String name() { fixtureName }
            @Override protected ColumnType scalarType() { ColumnType.DOUBLE }
            @Override protected double computeScalar(PocketGridContext ctx) { 0d }
        }
        PocketDescriptorRegistry.register(fixture)
        try {
            assertEquals(fixture, PocketDescriptorRegistry.get(fixtureName))
            assertTrue(PocketDescriptorRegistry.knownNames().contains(fixtureName))
        } finally {
            PocketDescriptorRegistry.unregister(fixtureName)
        }
        assertFalse(PocketDescriptorRegistry.knownNames().contains(fixtureName))
        assertThrows(PrankException) {
            PocketDescriptorRegistry.get(fixtureName)
        }
    }

    @Test
    void registryListsKnownNames() {
        assertTrue(PocketDescriptorRegistry.knownNames().containsAll(EXPECTED_REGISTERED_NAMES as Set))
    }

    @Test
    void columnTypesAreCorrect() {
        // Scalar descriptors return a 1-element columnTypes() list; multi-column
        // descriptors return the full list. Spot-check both groups.
        assertEquals([ColumnType.DOUBLE], PocketDescriptorRegistry.get('volume').columnTypes())
        assertEquals([ColumnType.DOUBLE], PocketDescriptorRegistry.get('sphericity').columnTypes())
        assertEquals([ColumnType.DOUBLE], PocketDescriptorRegistry.get('radius_of_gyration').columnTypes())
        assertEquals([ColumnType.INT], PocketDescriptorRegistry.get('num_residues').columnTypes())
        assertEquals([ColumnType.INT], PocketDescriptorRegistry.get('num_surface_atoms').columnTypes())
        assertEquals([ColumnType.INT], PocketDescriptorRegistry.get('num_grid_points').columnTypes())
        assertEquals([ColumnType.DOUBLE, ColumnType.DOUBLE, ColumnType.DOUBLE],
                PocketDescriptorRegistry.get('principal_moments').columnTypes())
    }

    // --- principal_moments ---

    /**
     * 3×3×3 cube centered at (1,1,1). All three principal axes are equivalent
     * (cube is isotropic in axes-aligned directions), so the gyration tensor's
     * eigenvalues all equal the per-dimension variance = 2/3.
     */
    @Test
    void principalMomentsOfCubeAreEqual() {
        PocketGrid grid = gridOfPoints(cube(3))
        TestPocket p = new TestPocket(); p.rank = 1
        double[] lambdas = new PrincipalMomentsDescriptor().compute(ctx(grid, p))
        assertEquals(3, lambdas.length)
        assertEquals(2d / 3d, lambdas[0], 1e-9d)
        assertEquals(2d / 3d, lambdas[1], 1e-9d)
        assertEquals(2d / 3d, lambdas[2], 1e-9d)
    }

    /**
     * Two points along the x-axis: gyration tensor has λ₁ = (per-dim variance
     * of x), the other two are zero. Distinguishes rod-like shape signatures.
     */
    @Test
    void principalMomentsOfTwoPointsAlongAxis() {
        Atom a = new Point(0d, 0d, 0d)
        Atom b = new Point(2d, 0d, 0d)
        LongIntHashMap idx = new LongIntHashMap()
        idx.put(PocketGrid.pack(0, 0, 0), 0)
        idx.put(PocketGrid.pack(2, 0, 0), 1)
        BitSet bs = new BitSet(); bs.set(0, 2)
        Map<Integer, BitSet> assigned = [(1): bs] as LinkedHashMap
        PocketGrid grid = new PocketGrid(new Atoms([a, b]), 1.0d, 0d, 0d, 0d, idx, assigned)
        TestPocket p = new TestPocket(); p.rank = 1
        double[] lambdas = new PrincipalMomentsDescriptor().compute(ctx(grid, p))
        // Per-dim variance of {0, 2} = mean((-1)² + 1²) = 1; other two dims have zero spread.
        assertEquals(1.0d, lambdas[0], 1e-9d)
        assertEquals(0.0d, lambdas[1], 1e-9d)
        assertEquals(0.0d, lambdas[2], 1e-9d)
    }

    @Test
    void principalMomentsEigenvaluesAreSortedDescending() {
        // Square in the xy plane: variance(x) = variance(y) = some value > 0,
        // variance(z) = 0. λ₁, λ₂ are equal (and > 0); λ₃ = 0. Verifies the sort.
        List<Atom> pts = [
                new Point(0d, 0d, 0d),
                new Point(2d, 0d, 0d),
                new Point(0d, 2d, 0d),
                new Point(2d, 2d, 0d),
        ]
        PocketGrid grid = gridOfPoints(pts)
        TestPocket p = new TestPocket(); p.rank = 1
        double[] lambdas = new PrincipalMomentsDescriptor().compute(ctx(grid, p))
        assertTrue(lambdas[0] >= lambdas[1], "λ₁ ≥ λ₂")
        assertTrue(lambdas[1] >= lambdas[2], "λ₂ ≥ λ₃")
        assertEquals(0.0d, lambdas[2], 1e-9d)  // flat in xy → λ₃ = 0
    }

    @Test
    void principalMomentsOfEmptyOrSinglePocketIsAllZeros() {
        // Cardinality < 2 short-circuits to zeros — see PrincipalMomentsDescriptor javadoc.
        PocketGrid empty = gridOfPoints([])
        TestPocket p = new TestPocket(); p.rank = 1
        double[] lambdas = new PrincipalMomentsDescriptor().compute(ctx(empty, p))
        assertArrayEquals([0.0d, 0.0d, 0.0d] as double[], lambdas, 0d)
    }

    /**
     * Pairs with {@code radius_of_gyration}: trace(gyration tensor) = sum of
     * eigenvalues = Rg². Pins the relationship between the two descriptors so
     * a future change to one without the other gets caught.
     */
    @Test
    void principalMomentsSumEqualsRadiusOfGyrationSquared() {
        PocketGrid grid = gridOfPoints(cube(3))
        TestPocket p = new TestPocket(); p.rank = 1
        double[] lambdas = new PrincipalMomentsDescriptor().compute(ctx(grid, p))
        double rg = new RadiusOfGyrationDescriptor().compute(ctx(grid, p))[0]
        double sum = lambdas[0] + lambdas[1] + lambdas[2]
        assertEquals(rg * rg, sum, 1e-9d)
    }

    /**
     * Boundary between the short-circuit ({@code n < 2}) and the full path.
     * Two distinct points span exactly one axis, so λ₁ > 0 and the other two
     * eigenvalues are 0. Per-dim variance of {0, 2} = mean((-1)² + 1²) = 1.
     */
    @Test
    void principalMomentsAtExactlyTwoDistinctPointsHitsFullPath() {
        List<Atom> pts = [new Point(0d, 0d, 0d), new Point(2d, 0d, 0d)]
        PocketGrid grid = gridOfPoints(pts)
        TestPocket p = new TestPocket(); p.rank = 1
        double[] lambdas = new PrincipalMomentsDescriptor().compute(ctx(grid, p))
        assertEquals(1.0d, lambdas[0], 1e-9d)
        assertEquals(0.0d, lambdas[1], 1e-9d)
        assertEquals(0.0d, lambdas[2], 1e-9d)
    }

    /**
     * Two coincident points: cardinality=2 takes the full path, but every
     * delta-from-centroid is 0, so the gyration tensor is zero and all
     * eigenvalues come out zero. Verifies the full path handles the
     * degenerate non-short-circuit case without producing NaN / negative
     * eigenvalues from numerical noise.
     */
    @Test
    void principalMomentsOfTwoCoincidentPointsIsAllZeros() {
        // LongIntHashMap can't hold two entries with the same packed key — for
        // a true "two atoms at the same coord" fixture we need two distinct
        // lattice slots whose Atom positions happen to coincide. Build by hand.
        Atom a = new Point(0d, 0d, 0d)
        Atom b = new Point(0d, 0d, 0d)
        LongIntHashMap idx = new LongIntHashMap()
        idx.put(PocketGrid.pack(0, 0, 0), 0)
        idx.put(PocketGrid.pack(1, 0, 0), 1)  // arbitrary distinct lattice slot
        BitSet bs = new BitSet(); bs.set(0, 2)
        Map<Integer, BitSet> assigned = [(1): bs] as LinkedHashMap
        PocketGrid grid = new PocketGrid(new Atoms([a, b]), 1.0d, 0d, 0d, 0d, idx, assigned)
        TestPocket p = new TestPocket(); p.rank = 1

        double[] lambdas = new PrincipalMomentsDescriptor().compute(ctx(grid, p))
        assertArrayEquals([0.0d, 0.0d, 0.0d] as double[], lambdas, 1e-9d)
    }

}
