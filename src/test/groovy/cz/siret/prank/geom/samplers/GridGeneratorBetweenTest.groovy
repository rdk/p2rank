package cz.siret.prank.geom.samplers

import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.AtomImpl
import org.biojava.nbio.structure.Element
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests {@link GridGenerator#sampleGridPointsBetween(Atoms, Atoms, double, double, double)}
 * — the dual-bound grid sampler used by the pocket-grid export feature. The atoms set
 * gates the per-cell VdW inner exclusion; the sasPoints set gates the bounding box
 * and the outer max-distance check.
 *
 * <p>Most tests below pass the same {@code Atoms} as both arguments — when SAS points
 * coincide with atom positions, the dual-bound contract degenerates to the original
 * "shell around atoms" semantics, which is the easiest case to assert against.
 */
@CompileStatic
class GridGeneratorBetweenTest {

    private static Atom carbonAt(double x, double y, double z) {
        AtomImpl a = new AtomImpl()
        a.element = Element.C
        a.name = "C"
        a.x = x; a.y = y; a.z = z
        return a
    }

    @Test
    void singleAtomShellExcludesVdwInteriorAndFarPoints() {
        // One carbon at origin, VdW ≈ 1.7 Å, buffer 0.5 Å → inner exclusion at ~2.2 Å.
        // Same atom plays as SAS point (outer bound 4.0 Å around it). Spacing 0.5 Å.
        Atoms atoms = new Atoms([carbonAt(0d, 0d, 0d)])
        Atoms result = GridGenerator.sampleGridPointsBetween(atoms, atoms, 0.5d, 4.0d, 0.5d).points()

        // Every kept point must satisfy both bounds against the single atom.
        Atom c = atoms.list[0]
        for (Atom p : result) {
            double dist = Math.sqrt(
                    (p.x - c.x) * (p.x - c.x) +
                    (p.y - c.y) * (p.y - c.y) +
                    (p.z - c.z) * (p.z - c.z))
            assertTrue(dist <= 4.0d, "point at dist=$dist exceeds maxDist")
            assertTrue(dist >= 2.0d, "point at dist=$dist intrudes into VdW shell")  // 1.7+0.5 ≈ 2.2, with tolerance
        }
        // Result must be non-empty (shell at 2..4 Å around origin contains plenty of lattice cells).
        assertTrue(result.count > 0)
    }

    @Test
    void twoAtomsExcludeOverlappingInterior() {
        // Atoms 4 Å apart along x. With buffer 0.5 and VdW 1.7, the shell around each
        // begins ~2.2 Å away — leaving a thin region between them.
        Atoms atoms = new Atoms([carbonAt(0d, 0d, 0d), carbonAt(4.0d, 0d, 0d)])
        Atoms result = GridGenerator.sampleGridPointsBetween(atoms, atoms, 0.5d, 5.0d, 0.5d).points()

        // No kept point may be inside either atom's exclusion shell.
        for (Atom p : result) {
            double d1 = Math.sqrt(p.x*p.x + p.y*p.y + p.z*p.z)
            double d2 = Math.sqrt((p.x-4.0d)*(p.x-4.0d) + p.y*p.y + p.z*p.z)
            double nearest = Math.min(d1, d2)
            assertTrue(nearest >= 2.0d, "point at dist=$nearest intrudes")
            assertTrue(nearest <= 5.0d, "point at dist=$nearest too far")
        }
        assertTrue(result.count > 0)
    }

    @Test
    void noPointSatisfiesBothBoundsReturnsEmpty() {
        // maxDist (0.1) < vdw(C) + buffer (1.7 + 0.5 = 2.2) so no point can satisfy
        // both bounds simultaneously. Sampler should return an empty Atoms set,
        // not crash or return out-of-band data.
        Atoms atoms = new Atoms([carbonAt(0d, 0d, 0d)])
        Atoms result = GridGenerator.sampleGridPointsBetween(atoms, atoms, 0.5d, 0.1d, 0.5d).points()
        assertEquals(0, result.count)
    }

    @Test
    void emptySasPointsReturnsEmpty() {
        // No pockets supplied SAS points → no center for the lattice. Sampler should
        // short-circuit to an empty result without touching atoms.
        Atoms atoms = new Atoms([carbonAt(0d, 0d, 0d)])
        Atoms result = GridGenerator.sampleGridPointsBetween(atoms, new Atoms(), 0.5d, 4.0d, 0.5d).points()
        assertEquals(0, result.count)
    }

    @Test
    void sasPointsDriveOuterBoundIndependentlyOfAtoms() {
        // SAS point at (10, 0, 0) drives the outer bound (maxDist=2.0 Å around it).
        // Atom is placed inside the SAS shell at (9, 0, 0) so its VdW exclusion zone
        // (~2.2 Å radius around the atom) carves a hole out of the kept set. This
        // genuinely exercises both bounds — keeping points where SAS is close AND atom
        // is far enough. To prove the carve-out fires, we compare against a control run
        // with the atom moved far away.
        Atoms atomNear = new Atoms([carbonAt(9d, 0d, 0d)])
        Atoms atomFar  = new Atoms([carbonAt(0d, 0d, 0d)])  // 10 Å from SAS — outside any inner-bound shell
        Atoms sas = new Atoms([new Point(10d, 0d, 0d) as Atom])

        Atoms withAtomNear = GridGenerator.sampleGridPointsBetween(atomNear, sas, 0.5d, 2.0d, 0.5d).points()
        Atoms withAtomFar  = GridGenerator.sampleGridPointsBetween(atomFar,  sas, 0.5d, 2.0d, 0.5d).points()

        assertTrue(withAtomFar.count > withAtomNear.count,
                "near atom should carve out cells via inner bound (far=${withAtomFar.count}, near=${withAtomNear.count})")

        // Every kept point in the near-atom run must satisfy both bounds.
        Atom atomN = atomNear.list[0]
        for (Atom p : withAtomNear) {
            double sasDist = Math.sqrt((p.x - 10d)*(p.x - 10d) + p.y*p.y + p.z*p.z)
            assertTrue(sasDist <= 2.0d, "point at sasDist=$sasDist exceeds maxDist")
            double atomDist = Math.sqrt((p.x - atomN.x)*(p.x - atomN.x) + (p.y - atomN.y)*(p.y - atomN.y) + (p.z - atomN.z)*(p.z - atomN.z))
            assertTrue(atomDist >= 2.0d, "point at atomDist=$atomDist intrudes into VdW shell")  // 1.7+0.5 ≈ 2.2
        }
    }

    @Test
    void nanCoordInSasPointsThrowsClearError() {
        // GridGenerator's (Box, edge) ctor guards against NaN/Inf input — without it,
        // IEEEremainder(NaN, edge) silently produces NaN origins and a NaN-everywhere
        // lattice. This test pins the throw so a future refactor that drops the guard
        // can't reintroduce silent NaN propagation.
        Atoms atoms = new Atoms([carbonAt(0d, 0d, 0d)])
        Atoms sasWithNaN = new Atoms([
                new Point(0d, 0d, 0d) as Atom,
                new Point(Double.NaN, 0d, 0d) as Atom
        ])
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class) {
            GridGenerator.sampleGridPointsBetween(atoms, sasWithNaN, 1.0d, 3.0d, 0.5d)
        } as IllegalArgumentException
        assertTrue(e.message.toLowerCase().contains('non-finite'),
                "expected non-finite-box error, got: ${e.message}")
    }

    @Test
    void returnedOriginMatchesGridShift() {
        // Sampler exposes the lattice origin it picked so downstream callers don't
        // recompute Box.aroundAtoms + shift. Sanity-check: the origin equals what
        // GridGenerator.shift would produce for the same box.
        Atoms sas = new Atoms([new Point(0d, 0d, 0d) as Atom, new Point(5d, 5d, 5d) as Atom])
        Atoms atoms = new Atoms([carbonAt(-10d, 0d, 0d)])  // far away — irrelevant for the box
        GridSample sample = GridGenerator.sampleGridPointsBetween(atoms, sas, 1.0d, 3.0d, 0.5d)

        // Box around SAS expanded by 3.0 Å on each side: min=(-3,-3,-3), max=(8,8,8) → shift(-3,8,1.0)
        double expectedOrigin = GridGenerator.shift(-3d, 8d, 1.0d)
        assertEquals(expectedOrigin, sample.originX(), 1e-12d)
        assertEquals(expectedOrigin, sample.originY(), 1e-12d)
        assertEquals(expectedOrigin, sample.originZ(), 1e-12d)
    }

}
