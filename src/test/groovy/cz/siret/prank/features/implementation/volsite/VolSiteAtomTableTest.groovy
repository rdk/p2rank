package cz.siret.prank.features.implementation.volsite

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.implementation.volsite.VolSitePharmacophore.AtomProps
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.AtomImpl
import org.biojava.nbio.structure.Element
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.AminoAcidImpl
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Behavioural tests for the per-protein VolSite atom-properties cache.
 *
 * <p>Parity: for every atom of a real protein, {@code table.get(atom)} must match
 * {@code VolSitePharmacophore.getAtomProperties(atom)} field-by-field — locking
 * the optimization as correctness-preserving.
 *
 * <p>Memoization: repeated {@code forProtein} calls return the same instance
 * (the table is built once per protein and cached on {@code Protein.secondaryData}).
 *
 * <p>Identity contract: {@code get(unknownAtom)} throws — silent NPE on a foreign
 * atom would mask a real bug (mutating {@code proteinAtoms} between table build
 * and lookup).
 */
@CompileStatic
class VolSiteAtomTableTest {

    private static final String PDB = 'distro/test_data/1fbl.pdb'

    private static Atom atomAt(String element, String resName, String atomName,
                                double x, double y, double z) {
        AtomImpl a = new AtomImpl()
        a.element = Element.valueOfIgnoreCase(element)
        a.name = atomName
        a.x = x; a.y = y; a.z = z
        Group g = new AminoAcidImpl()
        g.setPDBName(resName)
        a.setGroup(g)
        return a
    }

    @Test
    void tableMatchesPerCallGetAtomPropertiesForEveryAtom() {
        // The whole point of the optimization is correctness preservation. If a single
        // atom flips between the per-call and per-table paths, downstream descriptor
        // output drifts silently.
        Protein protein = Protein.load(PDB)
        VolSiteAtomTable table = VolSiteAtomTable.forProtein(protein)

        int checked = 0
        for (Atom atom : protein.proteinAtoms.list) {
            AtomProps expected = VolSitePharmacophore.getAtomProperties(atom)
            AtomProps actual = table.get(atom)

            assertEquals(expected.aromatic,    actual.aromatic,    "aromatic mismatch on ${atom.name}/${atom.group?.PDBName}")
            assertEquals(expected.cation,      actual.cation,      "cation mismatch on ${atom.name}/${atom.group?.PDBName}")
            assertEquals(expected.anion,       actual.anion,       "anion mismatch on ${atom.name}/${atom.group?.PDBName}")
            assertEquals(expected.hydrophobic, actual.hydrophobic, "hydrophobic mismatch on ${atom.name}/${atom.group?.PDBName}")
            assertEquals(expected.acceptor,    actual.acceptor,    "acceptor mismatch on ${atom.name}/${atom.group?.PDBName}")
            assertEquals(expected.donor,       actual.donor,       "donor mismatch on ${atom.name}/${atom.group?.PDBName}")
            checked++
        }
        // Sanity: 1fbl is non-trivial; assert we actually iterated something.
        assertTrue(checked > 100, "expected >100 atoms in 1fbl, got $checked")
    }

    @Test
    void repeatedForProteinReturnsSameInstance() {
        // Memoization on secondaryData is the whole point — a second call must NOT rebuild.
        Protein protein = Protein.load(PDB)
        VolSiteAtomTable first = VolSiteAtomTable.forProtein(protein)
        VolSiteAtomTable second = VolSiteAtomTable.forProtein(protein)
        assertSame(first, second, "forProtein must memoize on secondaryData")
    }

    @Test
    void getThrowsForAtomNotInProtein() {
        // Identity-keyed lookup: a foreign atom (one that was never put in the table)
        // must NOT silently return a stale or default AtomProps — the loud-throw is the
        // signal that proteinAtoms was mutated after build, which would otherwise
        // surface as a downstream NPE far from the cause.
        Protein protein = Protein.load(PDB)
        VolSiteAtomTable table = VolSiteAtomTable.forProtein(protein)

        Atom foreign = atomAt("C", "ALA", "C", 0d, 0d, 0d)
        IllegalStateException ex = assertThrows(IllegalStateException.class) {
            table.get(foreign)
        } as IllegalStateException
        assertTrue(ex.message.contains("VolSiteAtomTable"),
                "expected message to mention the table, got: ${ex.message}")
    }
}
