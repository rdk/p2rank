package cz.siret.prank.geom.cdksurface

import cz.siret.prank.geom.PatchedCdkNumericalSurface
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test
import org.openscience.cdk.interfaces.IAtomContainer
import org.openscience.cdk.silent.Atom
import org.openscience.cdk.silent.AtomContainer

import javax.vecmath.Point3d

import static org.junit.jupiter.api.Assertions.*

/**
 * Regression tests for {@link PatchedCdkNumericalSurface}.
 *
 * Mirrors the FMS-side {@code VdwFallbackTest} for the CDK wrapper path: ensures that
 * elements whose CDK {@code Elements} enum entry has {@code null} VdW radius
 * (Co, Ni, Cu, Rh, Os, Ir, plus radioactive / synthetic) survive surface computation
 * via the proxy-element substitution.
 */
@CompileStatic
class PatchedCdkNumericalSurfaceTest {

    private static IAtomContainer singleAtomContainer(String symbol) {
        AtomContainer c = new AtomContainer(1, 0, 0, 0)
        c.addAtom(new Atom(symbol, new Point3d(0.0d, 0.0d, 0.0d)))
        return c
    }

    @Test
    void cobaltAtomDoesNotCrash() {
        PatchedCdkNumericalSurface surface = new PatchedCdkNumericalSurface(
                singleAtomContainer("Co"), 1.4d, 4)
        assertTrue(surface.totalSurfaceArea > 0,
                "Cobalt atom should produce positive surface area, got ${surface.totalSurfaceArea}")
    }

    @Test
    void nickelAtomDoesNotCrash() {
        PatchedCdkNumericalSurface surface = new PatchedCdkNumericalSurface(
                singleAtomContainer("Ni"), 1.4d, 4)
        assertTrue(surface.totalSurfaceArea > 0)
    }

    @Test
    void copperAtomDoesNotCrash() {
        PatchedCdkNumericalSurface surface = new PatchedCdkNumericalSurface(
                singleAtomContainer("Cu"), 1.4d, 4)
        assertTrue(surface.totalSurfaceArea > 0)
    }

    @Test
    void carbonAtomMatchesUnpatchedBehaviour() {
        // Carbon has a non-null VdW in CDK's enum, so the wrapper must NOT substitute it.
        // Its surface should match the analytic 4*pi*r^2 within tessellation tolerance.
        PatchedCdkNumericalSurface surface = new PatchedCdkNumericalSurface(
                singleAtomContainer("C"), 1.4d, 4)
        double expected = 4.0d * Math.PI * (1.7d + 1.4d) * (1.7d + 1.4d)  // CDK C VdW = 1.7
        assertEquals(expected, surface.totalSurfaceArea, expected * 0.01)
    }

    @Test
    void originalContainerIsNotMutated() {
        // The wrapper builds an internal patched copy. The caller's container must keep
        // its original atom symbols.
        IAtomContainer original = singleAtomContainer("Co")
        assertEquals("Co", original.getAtom(0).symbol)
        new PatchedCdkNumericalSurface(original, 1.4d, 4)
        assertEquals("Co", original.getAtom(0).symbol,
                "Wrapper must not mutate the caller's atom container")
    }

    @Test
    void nullVdwSymbolSetIsImmutable() {
        assertThrows(UnsupportedOperationException.class, {
            PatchedCdkNumericalSurface.NULL_VDW_SYMBOLS.add("Xx")
        } as org.junit.jupiter.api.function.Executable)
    }
}
