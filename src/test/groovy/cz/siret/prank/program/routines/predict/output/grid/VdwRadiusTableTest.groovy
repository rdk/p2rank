package cz.siret.prank.program.routines.predict.output.grid

import cz.siret.prank.geom.Point
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.AtomImpl
import org.biojava.nbio.structure.Element
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class VdwRadiusTableTest {

    private static AtomImpl atomOf(String elementSymbol) {
        AtomImpl a = new AtomImpl()
        a.element = Element.valueOfIgnoreCase(elementSymbol)
        a.name = elementSymbol
        a.x = 0; a.y = 0; a.z = 0
        return a
    }

    @Test
    void knownElementsReturnCdkRadii() {
        // Carbon ~1.7, Nitrogen ~1.55, Oxygen ~1.52 per CDK's radii-vdw.txt
        assertTrue(VdwRadiusTable.get(atomOf("C")) > 1.0d)
        assertTrue(VdwRadiusTable.get(atomOf("C")) < 2.5d)
        assertTrue(VdwRadiusTable.get(atomOf("N")) > 1.0d)
        assertTrue(VdwRadiusTable.get(atomOf("O")) > 1.0d)
        assertTrue(VdwRadiusTable.get(atomOf("S")) > 1.5d)
    }

    @Test
    void copperFallsBackToKrypton() {
        // Cu has null VdW in CDK's Elements enum — should hit the fallback.
        assertEquals(VdwRadiusTable.FALLBACK_VDW, VdwRadiusTable.get(atomOf("Cu")), 1e-9d)
    }

    @Test
    void cobaltAndNickelFallBack() {
        assertEquals(VdwRadiusTable.FALLBACK_VDW, VdwRadiusTable.get(atomOf("Co")), 1e-9d)
        assertEquals(VdwRadiusTable.FALLBACK_VDW, VdwRadiusTable.get(atomOf("Ni")), 1e-9d)
    }

    @Test
    void pointAtomDefaultsToCarbon() {
        // Bare geom Point has no Element; should fall back to "C" via the resolver.
        double r = VdwRadiusTable.get(new Point(0d, 0d, 0d))
        assertEquals(VdwRadiusTable.get(atomOf("C")), r, 1e-9d)
    }

}
