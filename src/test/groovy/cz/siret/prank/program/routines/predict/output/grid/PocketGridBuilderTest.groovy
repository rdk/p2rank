package cz.siret.prank.program.routines.predict.output.grid

import cz.siret.prank.program.routines.predict.output.grid.fill.FillKnobs
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.geom.Struct
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.AtomImpl
import org.biojava.nbio.structure.Element
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests {@link PocketGridBuilder}. Uses a synthetic Protein assembled from
 * raw atom positions plus synthetic Pockets (no PDB loading) so the test
 * stays fast and isolates the builder from the rest of the prediction
 * pipeline.
 *
 * <p>The lattice extent is driven by the protein atoms (a shell within
 * {@code maxDist} of an atom, outside its vdW + {@code atomBuffer} volume).
 * Pockets supply {@code sasPoints}, which drive only the per-pocket assignment
 * (a grid point is assigned to pocket P if it falls within {@code assignCutoff}
 * of any of P's SAS points). Tests put atoms and SAS points at convenient
 * coordinates that make geometric assertions easy to write.
 */
@CompileStatic
class PocketGridBuilderTest {

    /** Concrete empty Pocket subclass for tests (Pocket is abstract). */
    private static class TestPocket extends Pocket {}

    private static Atom carbonAt(double x, double y, double z) {
        AtomImpl a = new AtomImpl()
        a.element = Element.C
        a.name = "C"
        a.x = x; a.y = y; a.z = z
        return a
    }

    private static Atoms sasAt(double... xyz) {
        List<Atom> pts = new ArrayList<>()
        for (int i = 0; i < xyz.length; i += 3) {
            pts.add(new Point(xyz[i], xyz[i + 1], xyz[i + 2]) as Atom)
        }
        return new Atoms(pts)
    }

    private static PocketGridConfig defaultConfig() {
        new PocketGridConfig(
                1.0d,     // spacing
                6.0d,     // maxDist
                0.5d,     // atomBuffer
                4.5d,     // assignCutoff
                'kdtree', // assignerStrategy
                'none',   // fillStrategy — keep raw shell for predictable test assertions
                new FillKnobs.None())
    }

    private static Protein proteinWith(Atoms protAtoms) {
        Protein p = new Protein()
        p.proteinAtoms = protAtoms
        return p
    }

    @Test
    void gridCoversAtomShellEvenWithNoPockets() {
        // The grid extent is atom-driven, so with no pockets the lattice is still a
        // (non-empty) shell around the protein atoms — there are just no pockets to
        // assign points to. (Pre-revert this returned an empty grid because the SAS
        // union drove the bounding box.)
        Protein protein = proteinWith(new Atoms([carbonAt(0d, 0d, 0d)]))
        PocketGrid grid = PocketGridBuilder.build(protein, [] as List<Pocket>, defaultConfig())

        assertTrue(grid.pointCount > 0, "atom-driven grid should have points even with no pockets")
        assertEquals(0, grid.pocketCount)
    }

    @Test
    void assignsGridPointsToASinglePocket() {
        // Two-atom protein along x. The lattice box covers the atom shell; the pocket's
        // SAS point sits between the atoms and the per-pocket assignment runs against it.
        Atom a1 = carbonAt(0d, 0d, 0d)
        Atom a2 = carbonAt(6d, 0d, 0d)
        Protein protein = proteinWith(new Atoms([a1, a2]))

        Atoms sas = sasAt(3d, 0d, 0d)  // single SAS point at midpoint
        TestPocket pocket = new TestPocket()
        pocket.rank = 1
        pocket.sasPoints = sas

        PocketGrid grid = PocketGridBuilder.build(protein, [pocket] as List<Pocket>, defaultConfig())

        assertEquals(1, grid.pocketCount)
        BitSet assigned = grid.indicesForPocket(1)
        assertFalse(assigned.empty, "pocket should have assigned grid points")

        // Every assigned point must be within assign_cutoff (4.5 Å) of at least one SAS point.
        Atom sasPoint = sas.list[0]
        for (int idx = assigned.nextSetBit(0); idx >= 0; idx = assigned.nextSetBit(idx + 1)) {
            Atom p = grid.allPoints.list[idx]
            double d = Struct.dist(p, sasPoint)
            assertTrue(d <= 4.5d, "assigned point at dist=${d} exceeds cutoff")
        }
    }

    @Test
    void allowsMultiPocketMembership() {
        // Two pockets whose SAS-point neighborhoods overlap — grid points in the overlap
        // region should be in BOTH pockets' assignment sets.
        Atom a1 = carbonAt(0d, 0d, 0d)
        Atom a2 = carbonAt(8d, 0d, 0d)
        Protein protein = proteinWith(new Atoms([a1, a2]))

        TestPocket p1 = new TestPocket()
        p1.rank = 1
        p1.sasPoints = sasAt(3d, 0d, 0d)   // close enough to p2's SAS for overlap at 4.5Å cutoff

        TestPocket p2 = new TestPocket()
        p2.rank = 2
        p2.sasPoints = sasAt(5d, 0d, 0d)

        PocketGrid grid = PocketGridBuilder.build(protein, [p1, p2] as List<Pocket>, defaultConfig())

        BitSet in1 = grid.indicesForPocket(1)
        BitSet in2 = grid.indicesForPocket(2)
        BitSet both = (BitSet) in1.clone()
        both.and(in2)
        assertFalse(both.empty, "overlap region must produce multi-pocket points")
    }

    @Test
    void unknownFillStrategyThrows() {
        Protein protein = proteinWith(new Atoms([carbonAt(0d, 0d, 0d)]))
        PocketGridConfig bad = new PocketGridConfig(
                1.0d, 6.0d, 0.5d, 4.5d, 'kdtree', 'cubist', new FillKnobs.None())
        TestPocket p = new TestPocket()
        p.rank = 1
        p.sasPoints = sasAt(0d, 0d, 0d)
        assertThrows(cz.siret.prank.program.PrankException) {
            PocketGridBuilder.build(protein, [p] as List<Pocket>, bad)
        }
    }

    @Test
    void unknownAssignerStrategyThrows() {
        Protein protein = proteinWith(new Atoms([carbonAt(0d, 0d, 0d)]))
        PocketGridConfig bad = new PocketGridConfig(
                1.0d, 6.0d, 0.5d, 4.5d, 'rocketship', 'none', new FillKnobs.None())
        TestPocket p = new TestPocket()
        p.rank = 1
        p.sasPoints = sasAt(0d, 0d, 0d)
        assertThrows(cz.siret.prank.program.PrankException) {
            PocketGridBuilder.build(protein, [p] as List<Pocket>, bad)
        }
    }

    @Test
    void bothAssignersProduceIdenticalRawShells() {
        // Same input, two strategies → same raw shell (BitSet equality). Spec contract:
        // the assigner choice only affects how fast we compute the shell, not its contents.
        Atom a1 = carbonAt(0d, 0d, 0d)
        Atom a2 = carbonAt(6d, 0d, 0d)
        Protein protein = proteinWith(new Atoms([a1, a2]))

        TestPocket pocket = new TestPocket()
        pocket.rank = 1
        pocket.sasPoints = sasAt(3d, 0d, 0d)

        PocketGridConfig kd = new PocketGridConfig(
                1.0d, 6.0d, 0.5d, 4.5d, 'kdtree',     'none', new FillKnobs.None())
        PocketGridConfig vh = new PocketGridConfig(
                1.0d, 6.0d, 0.5d, 4.5d, 'voxel_hash', 'none', new FillKnobs.None())

        PocketGrid kdGrid = PocketGridBuilder.build(protein, [pocket] as List<Pocket>, kd)
        PocketGrid vhGrid = PocketGridBuilder.build(protein, [pocket] as List<Pocket>, vh)

        BitSet kdShell = kdGrid.indicesForPocket(1)
        BitSet vhShell = vhGrid.indicesForPocket(1)
        assertFalse(kdShell.empty)
        assertEquals(kdShell, vhShell, "assigner strategies must produce identical raw shells")
    }

    @Test
    void bothAssignersAgreeOnOffLatticeQueryPoint() {
        // Regression: VoxelHashAssigner used (di² + dj² + dk²) × spacing² as the
        // pre-prune lower bound — too tight whenever q sat off-lattice (the
        // common case). Pick spacing=1.2, cutoff=2.5, and a SAS point at
        // (0.59,0,0) so that the cell at (di=2,dj=1,dk=0) — world dist ≈ 2.17 Å
        // from q — is correctly in-range but the broken prune used to skip it.
        Atom anchor = carbonAt(0d, 0d, 0d)
        Atom shell  = carbonAt(5d, 0d, 0d)
        Protein protein = proteinWith(new Atoms([anchor, shell]))

        TestPocket pocket = new TestPocket()
        pocket.rank = 1
        pocket.sasPoints = sasAt(0.59d, 0d, 0d)

        PocketGridConfig kd = new PocketGridConfig(
                1.2d, 5.0d, 0.5d, 2.5d, 'kdtree',     'none', new FillKnobs.None())
        PocketGridConfig vh = new PocketGridConfig(
                1.2d, 5.0d, 0.5d, 2.5d, 'voxel_hash', 'none', new FillKnobs.None())

        BitSet kdShell = PocketGridBuilder.build(protein, [pocket] as List<Pocket>, kd).indicesForPocket(1)
        BitSet vhShell = PocketGridBuilder.build(protein, [pocket] as List<Pocket>, vh).indicesForPocket(1)
        assertFalse(kdShell.empty)
        assertEquals(kdShell, vhShell, "off-lattice query: voxel-hash must agree with kdtree")
    }

    @Test
    void emptySasPointsYieldsEmptyAssignment() {
        // Edge case: a pocket without SAS points gets an empty assignment (the grid
        // itself is still built around the protein atoms). The builder must not NPE.
        Atom a1 = carbonAt(0d, 0d, 0d)
        Protein protein = proteinWith(new Atoms([a1]))

        TestPocket pocket = new TestPocket()
        pocket.rank = 1
        pocket.sasPoints = new Atoms()

        PocketGrid grid = PocketGridBuilder.build(protein, [pocket] as List<Pocket>, defaultConfig())
        assertEquals(0, grid.indicesForPocket(1).cardinality())
    }

}
