package cz.siret.prank.features.implementation.energy2.calc

import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.AtomImpl
import org.biojava.nbio.structure.Element
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.AminoAcidImpl
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for AtomRole classification
 */
@CompileStatic
class AtomRoleTest {

    @Test
    void testBackboneAtomClassification() {
        AtomRole backboneN = AtomRole.classify(createAtom("N", "GLY", "N"))
        AtomRole backboneO = AtomRole.classify(createAtom("O", "GLY", "O"))
        AtomRole backboneC = AtomRole.classify(createAtom("C", "GLY", "C"))

        // backbone N should be donor
        assertTrue(backboneN.isDonor)
        assertFalse(backboneN.isAcceptor)
        assertEquals(1, backboneN.roleClassID)

        // backbone O should be acceptor
        assertFalse(backboneO.isDonor)
        assertTrue(backboneO.isAcceptor)
        assertEquals(2, backboneO.roleClassID)

        // backbone C should have no special role
        assertFalse(backboneC.isDonor)
        assertFalse(backboneC.isAcceptor)
        assertEquals(0, backboneC.roleClassID)
    }

    @Test
    void testArginineClassification() {
        AtomRole argNE = AtomRole.classify(createAtom("N", "ARG", "NE"))
        AtomRole argNH1 = AtomRole.classify(createAtom("N", "ARG", "NH1"))
        AtomRole argNH2 = AtomRole.classify(createAtom("N", "ARG", "NH2"))
        AtomRole argCB = AtomRole.classify(createAtom("C", "ARG", "CB"))

        // guanidinium nitrogens should be donors
        assertTrue(argNE.isDonor && !argNE.isAcceptor && argNE.roleClassID == 3)
        assertTrue(argNH1.isDonor && !argNH1.isAcceptor && argNH1.roleClassID == 3)
        assertTrue(argNH2.isDonor && !argNH2.isAcceptor && argNH2.roleClassID == 3)

        // side chain carbons should have no special role
        assertTrue(!argCB.isDonor && !argCB.isAcceptor && argCB.roleClassID == 0)
    }

    @Test
    void testAsparticAcidClassification() {
        AtomRole aspOD1 = AtomRole.classify(createAtom("O", "ASP", "OD1"))
        AtomRole aspOD2 = AtomRole.classify(createAtom("O", "ASP", "OD2"))
        AtomRole aspCG = AtomRole.classify(createAtom("C", "ASP", "CG"))

        // carboxylate oxygens should be acceptors
        assertTrue(!aspOD1.isDonor && aspOD1.isAcceptor && aspOD1.roleClassID == 6)
        assertTrue(!aspOD2.isDonor && aspOD2.isAcceptor && aspOD2.roleClassID == 6)

        // carboxyl carbon should have no special role
        assertTrue(!aspCG.isDonor && !aspCG.isAcceptor && aspCG.roleClassID == 0)
    }

    @Test
    void testGlutamicAcidClassification() {
        AtomRole gluOE1 = AtomRole.classify(createAtom("O", "GLU", "OE1"))
        AtomRole gluOE2 = AtomRole.classify(createAtom("O", "GLU", "OE2"))

        // carboxylate oxygens should be acceptors
        assertTrue(!gluOE1.isDonor && gluOE1.isAcceptor && gluOE1.roleClassID == 10)
        assertTrue(!gluOE2.isDonor && gluOE2.isAcceptor && gluOE2.roleClassID == 10)
    }

    @Test
    void testAsparagineClassification() {
        AtomRole asnND2 = AtomRole.classify(createAtom("N", "ASN", "ND2"))
        AtomRole asnOD1 = AtomRole.classify(createAtom("O", "ASN", "OD1"))

        // amide nitrogen should be weak donor
        assertTrue(asnND2.isDonor && !asnND2.isAcceptor && asnND2.roleClassID == 4)

        // amide oxygen should be acceptor
        assertTrue(!asnOD1.isDonor && asnOD1.isAcceptor && asnOD1.roleClassID == 5)
    }

    @Test
    void testGlutamineClassification() {
        AtomRole glnNE2 = AtomRole.classify(createAtom("N", "GLN", "NE2"))
        AtomRole glnOE1 = AtomRole.classify(createAtom("O", "GLN", "OE1"))

        // amide nitrogen should be weak donor
        assertTrue(glnNE2.isDonor && !glnNE2.isAcceptor && glnNE2.roleClassID == 8)

        // amide oxygen should be acceptor
        assertTrue(!glnOE1.isDonor && glnOE1.isAcceptor && glnOE1.roleClassID == 9)
    }

    @Test
    void testHistidineClassification() {
        AtomRole hisND1 = AtomRole.classify(createAtom("N", "HIS", "ND1"))
        AtomRole hisNE2 = AtomRole.classify(createAtom("N", "HIS", "NE2"))

        // ring nitrogens should be both donors and acceptors
        assertTrue(hisND1.isDonor && hisND1.isAcceptor && hisND1.roleClassID == 11)
        assertTrue(hisNE2.isDonor && hisNE2.isAcceptor && hisNE2.roleClassID == 11)
    }

    @Test
    void testLysineClassification() {
        AtomRole lysNZ = AtomRole.classify(createAtom("N", "LYS", "NZ"))

        // ammonium nitrogen should be donor
        assertTrue(lysNZ.isDonor && !lysNZ.isAcceptor && lysNZ.roleClassID == 12)
    }

    @Test
    void testSerineClassification() {
        AtomRole serOG = AtomRole.classify(createAtom("O", "SER", "OG"))

        // hydroxyl oxygen should be both donor and acceptor
        assertTrue(serOG.isDonor && serOG.isAcceptor && serOG.roleClassID == 13)
    }

    @Test
    void testThreonineClassification() {
        AtomRole thrOG1 = AtomRole.classify(createAtom("O", "THR", "OG1"))

        // hydroxyl oxygen should be both donor and acceptor
        assertTrue(thrOG1.isDonor && thrOG1.isAcceptor && thrOG1.roleClassID == 14)
    }

    @Test
    void testTryptophanClassification() {
        AtomRole trpNE1 = AtomRole.classify(createAtom("N", "TRP", "NE1"))

        // indole nitrogen should be donor
        assertTrue(trpNE1.isDonor && !trpNE1.isAcceptor && trpNE1.roleClassID == 15)
    }

    @Test
    void testTyrosineClassification() {
        AtomRole tyrOH = AtomRole.classify(createAtom("O", "TYR", "OH"))

        // phenolic oxygen should be both donor and acceptor
        assertTrue(tyrOH.isDonor && tyrOH.isAcceptor && tyrOH.roleClassID == 16)
    }

    @Test
    void testCysteineClassification() {
        AtomRole cysSG = AtomRole.classify(createAtom("S", "CYS", "SG"))

        // sulfur should be both weak donor and acceptor
        assertTrue(cysSG.isDonor && cysSG.isAcceptor && cysSG.roleClassID == 7)
    }

    @Test
    void testUnknownResidueClassification() {
        AtomRole unknownAtom = AtomRole.classify(createAtom("C", "XYZ", "CB"))

        // should have no special role
        assertFalse(unknownAtom.isDonor)
        assertFalse(unknownAtom.isAcceptor)
        assertEquals(0, unknownAtom.roleClassID)
    }

    @Test
    void testNullAtomHandling() {
        AtomRole nullRole = AtomRole.classify(null)

        // should return default role
        assertFalse(nullRole.isDonor)
        assertFalse(nullRole.isAcceptor)
        assertEquals(0, nullRole.roleClassID)
    }

    @Test
    void testCaseInsensitiveClassification() {
        AtomRole lowerCase = AtomRole.classify(createAtom("N", "arg", "nh1"))
        AtomRole upperCase = AtomRole.classify(createAtom("N", "ARG", "NH1"))

        // both should have same classification
        assertEquals(lowerCase.isDonor, upperCase.isDonor)
        assertEquals(lowerCase.isAcceptor, upperCase.isAcceptor)
        assertEquals(lowerCase.roleClassID, upperCase.roleClassID)
    }

    // Helper method to create test atoms
    private Atom createAtom(String element, String resName, String atomName) {
        Atom atom = new AtomImpl()
        atom.setElement(Element.valueOfIgnoreCase(element))
        atom.setName(atomName)

        Group group = new AminoAcidImpl()
        group.setPDBName(resName)
        atom.setGroup(group)

        return atom
    }
}