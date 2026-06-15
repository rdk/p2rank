package cz.siret.prank.geom

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.loaders.LoaderParams
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.AtomImpl
import org.biojava.nbio.structure.Chain
import org.biojava.nbio.structure.ChainImpl
import org.biojava.nbio.structure.Element
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.HetatomImpl
import org.biojava.nbio.structure.ResidueNumber
import org.biojava.nbio.structure.Structure
import org.biojava.nbio.structure.StructureImpl
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests for {@link AlternateChainReducer} - collapsing alternate-conformation chains (the 6een pattern)
 * while leaving ordinary structures and within-residue altLocs untouched.
 */
@CompileStatic
class AlternateChainReducerTest {

    static final String ORDINARY_ALTLOC_PROTEIN = "distro/test_data/2W83.pdb"  // within-residue altLocs only

    private static Atom atom(String name, double x, double y, double z, Character altLoc, int serial) {
        Atom a = new AtomImpl()
        a.setName(name)
        a.setElement(Element.C)
        a.setCoords([x, y, z] as double[])
        a.setPDBserial(serial)
        if (altLoc != null) {
            a.setAltLoc(altLoc)
        }
        return a
    }

    /** A chain of 3 residues x 4 atoms, all carrying {@code altLoc}, centred at (cx, cy, cz). */
    private static Chain chain(String id, double cx, double cy, double cz, Character altLoc, int baseSerial) {
        Chain ch = new ChainImpl()
        ch.setId(id)
        ch.setName(id)
        int serial = baseSerial
        for (int r = 1; r <= 3; r++) {
            Group g = new HetatomImpl()
            g.setPDBName("ALA")
            g.setResidueNumber(new ResidueNumber(id, r, null as Character))
            g.addAtom(atom("N",  cx + r, cy,     cz,     altLoc, serial++))
            g.addAtom(atom("CA", cx + r, cy + 1, cz,     altLoc, serial++))
            g.addAtom(atom("C",  cx + r, cy + 1, cz + 1, altLoc, serial++))
            g.addAtom(atom("O",  cx + r, cy,     cz + 1, altLoc, serial++))
            ch.addGroup(g)
        }
        return ch
    }

    private static Structure structureOf(List<Chain> chains) {
        Structure s = new StructureImpl()
        s.setPdbId(null)
        for (Chain ch : chains) {
            s.addChain(ch)
        }
        return s
    }

    private static List<String> chainIds(Structure s) {
        return s.getChains().collect { Chain c -> c.getId() }
    }

    @Test
    void dropsSuperimposedHigherLetterAlternateChains() {
        Character A = 'A' as char
        Character B = 'B' as char
        Character C = 'C' as char
        // A (altLoc A) and B (altLoc B) and C (altLoc C) are superimposed -> B and C are redundant alternates of A
        Structure s = structureOf([
                chain("A", 10, 10, 10, A, 1),
                chain("B", 10.01, 10, 10, B, 1001),
                chain("C", 10.0, 10.01, 10, C, 2001),
        ])

        Structure reduced = AlternateChainReducer.reduceAlternateConformationChains(s, "synthetic")

        assertEquals(["A"], chainIds(reduced),
                "only the primary (lowest-letter) conformation A should survive")
    }

    @Test
    void keepsSeparateMoleculesAndBlankChains() {
        Character A = 'A' as char
        Character B = 'B' as char
        // X (altLoc A) and Y (altLoc B) superimposed -> Y dropped.
        // L is a blank-altLoc chain far away (ligand/separate molecule) -> kept.
        // Z is altLoc A but far away (genuine separate copy sharing a letter) -> kept.
        Structure s = structureOf([
                chain("X", 0, 0, 0, A, 1),
                chain("Y", 0.01, 0, 0, B, 1001),
                chain("L", 50, 50, 50, null, 2001),
                chain("Z", 90, 90, 90, A, 3001),
        ])

        Structure reduced = AlternateChainReducer.reduceAlternateConformationChains(s, "synthetic")

        assertEquals(["X", "L", "Z"] as Set, chainIds(reduced) as Set,
                "only the superimposed higher-letter alternate Y should be dropped; separate molecules and blank chains kept")
    }

    @Test
    void noOpReturnsSameInstanceWhenNoAlternateChains() {
        Structure s = structureOf([
                chain("A", 0, 0, 0, null, 1),
                chain("B", 40, 40, 40, null, 1001),
        ])

        Structure reduced = AlternateChainReducer.reduceAlternateConformationChains(s, "synthetic")

        assertSame(s, reduced, "structure with no alternate-conformation chains must be returned unchanged")
    }

    @Test
    void sameLetterSuperimposedChainsAreNotCollapsed() {
        Character A = 'A' as char
        // Two chains both tagged altLoc A, superimposed. Same letter => treated as distinct molecules, both kept.
        Structure s = structureOf([
                chain("A", 5, 5, 5, A, 1),
                chain("B", 5.01, 5, 5, A, 1001),
        ])

        Structure reduced = AlternateChainReducer.reduceAlternateConformationChains(s, "synthetic")

        assertSame(s, reduced, "chains sharing the same altLoc letter must never be collapsed against each other")
    }

    @Test
    void ordinaryWithinResidueAltLocsAreUntouched() {
        // 2W83 has only ordinary within-residue altLocs (already collapsed by the parser); the reducer must
        // not fire on it.
        Protein protein = Protein.load(ORDINARY_ALTLOC_PROTEIN, new LoaderParams())
        Structure reduced = AlternateChainReducer.reduceAlternateConformationChains(protein.structure, "2W83")

        assertSame(protein.structure, reduced,
                "ordinary within-residue altLoc structure must be returned unchanged")
    }

}
