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
 * Tests {@link GridGenerator#sampleGridPointsBetween(Atoms, double, double, double)}
 * — the grid sampler used by the pocket-grid export feature. A single point set (the
 * protein atoms) gates both bounds: a lattice cell is kept if it is within
 * {@code maxDist} of the nearest atom (outer bound) but outside that atom's VdW shell
 * plus {@code atomBuffer} (inner bound). The grid is therefore a shell around the whole
 * protein; per-pocket SAS points only restrict assignment downstream, not the extent.
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
        // Outer bound 4.0 Å around it. Spacing 0.5 Å.
        Atoms atoms = new Atoms([carbonAt(0d, 0d, 0d)])
        Atoms result = GridGenerator.sampleGridPointsBetween(atoms, 0.5d, 4.0d, 0.5d).points()

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
        Atoms result = GridGenerator.sampleGridPointsBetween(atoms, 0.5d, 5.0d, 0.5d).points()

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
        Atoms result = GridGenerator.sampleGridPointsBetween(atoms, 0.5d, 0.1d, 0.5d).points()
        assertEquals(0, result.count)
    }

    @Test
    void emptyAtomsReturnsEmpty() {
        // No atoms → no center for the lattice. Sampler should short-circuit to an
        // empty result rather than building a degenerate box.
        Atoms result = GridGenerator.sampleGridPointsBetween(new Atoms(), 0.5d, 4.0d, 0.5d).points()
        assertEquals(0, result.count)
    }

    @Test
    void nanCoordInAtomsThrowsClearError() {
        // GridGenerator's (Box, edge) ctor guards against NaN/Inf input — without it,
        // IEEEremainder(NaN, edge) silently produces NaN origins and a NaN-everywhere
        // lattice. This test pins the throw so a future refactor that drops the guard
        // can't reintroduce silent NaN propagation.
        Atoms atomsWithNaN = new Atoms([
                carbonAt(0d, 0d, 0d),
                carbonAt(Double.NaN, 0d, 0d)
        ])
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class) {
            GridGenerator.sampleGridPointsBetween(atomsWithNaN, 1.0d, 3.0d, 0.5d)
        } as IllegalArgumentException
        assertTrue(e.message.toLowerCase().contains('non-finite'),
                "expected non-finite-box error, got: ${e.message}")
    }

    @Test
    void returnedOriginMatchesGridShift() {
        // Sampler exposes the lattice origin it picked so downstream callers don't
        // recompute Box.aroundAtoms + shift. Sanity-check: the origin equals what
        // GridGenerator.shift would produce for the same box (around the atoms).
        Atoms atoms = new Atoms([carbonAt(0d, 0d, 0d), carbonAt(5d, 5d, 5d)])
        GridSample sample = GridGenerator.sampleGridPointsBetween(atoms, 1.0d, 3.0d, 0.5d)

        // Box around atoms expanded by 3.0 Å on each side: min=(-3,-3,-3), max=(8,8,8) → shift(-3,8,1.0)
        double expectedOrigin = GridGenerator.shift(-3d, 8d, 1.0d)
        assertEquals(expectedOrigin, sample.originX(), 1e-12d)
        assertEquals(expectedOrigin, sample.originY(), 1e-12d)
        assertEquals(expectedOrigin, sample.originZ(), 1e-12d)
    }

}
