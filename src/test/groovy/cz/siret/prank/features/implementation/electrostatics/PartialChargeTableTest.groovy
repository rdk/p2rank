package cz.siret.prank.features.implementation.electrostatics

import cz.siret.prank.domain.Protein
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.AtomImpl
import org.biojava.nbio.structure.Element
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.AminoAcidImpl
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class PartialChargeTableTest {

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
    void everyProteinAtomGetsFiniteCharge() {
        // No NaN survives the build step. Atoms outside the AMBER table get
        // the element fallback — never NaN.
        Protein protein = Protein.load(PDB)
        PartialChargeTable table = PartialChargeTable.forProtein(protein)

        int n = 0
        for (Atom atom : protein.proteinAtoms.list) {
            double q = table.get(atom)
            assertTrue(Double.isFinite(q),
                    "non-finite charge on ${atom.name}/${atom.group?.PDBName}: $q")
            n++
        }
        assertTrue(n > 100, "expected >100 atoms in 1fbl, got $n")
    }

    @Test
    void forProteinIsMemoized() {
        Protein protein = Protein.load(PDB)
        PartialChargeTable first = PartialChargeTable.forProtein(protein)
        PartialChargeTable second = PartialChargeTable.forProtein(protein)
        assertSame(first, second, "forProtein must memoize on secondaryData")
    }

    @Test
    void foreignAtomGetsElementFallback() {
        // A foreign Atom (one constructed outside the protein) returns the element-bucket
        // fallback charge rather than throwing. Required because pocket-level descriptors
        // iterate pocket.surfaceAtoms which is occasionally sourced from atom refs that
        // aren't the literal IdentityHashMap keys of proteinAtoms.list.
        Protein protein = Protein.load(PDB)
        PartialChargeTable table = PartialChargeTable.forProtein(protein)

        Atom foreignC = atomAt("C", "ALA", "CA", 0d, 0d, 0d)
        assertEquals(PartialChargeTable.elementFallback(Element.C), table.get(foreignC), 1e-9d,
                "foreign atom must fall back to element-bucket charge")

        Atom foreignO = atomAt("O", "ALA", "O", 0d, 0d, 0d)
        assertEquals(PartialChargeTable.elementFallback(Element.O), table.get(foreignO), 1e-9d)
    }

    @Test
    void elementFallbackIsDeterministicAndNeverNaN() {
        // Element-bucket defaults that are NOT from AMBER ff14SB — see
        // PartialChargeTable.elementFallback javadoc for derivation.
        assertEquals(-0.10d, PartialChargeTable.elementFallback(Element.C), 1e-9d)
        assertEquals(-0.50d, PartialChargeTable.elementFallback(Element.O), 1e-9d)
        assertEquals(+0.10d, PartialChargeTable.elementFallback(Element.H), 1e-9d)
        assertTrue(PartialChargeTable.elementFallback(Element.Zn) > 0)
        assertTrue(PartialChargeTable.elementFallback(Element.Fe) > 0)
        assertTrue(PartialChargeTable.elementFallback(Element.Cl) < 0)
        // Unknown element returns 0, never NaN.
        assertEquals(0d, PartialChargeTable.elementFallback(null), 1e-9d)
    }

    @Test
    void fallbackCountTracksDriftHits() {
        // Fresh table built from proteinAtoms: every atom in proteinAtoms hits the
        // build-time map → no fallbacks. Foreign atoms increment the counter.
        Protein protein = Protein.load(PDB)
        PartialChargeTable table = PartialChargeTable.forProtein(protein)

        assertEquals(0L, table.fallbackCount, "fresh table from proteinAtoms has zero fallbacks")
        for (Atom atom : protein.proteinAtoms.list) table.get(atom)
        assertEquals(0L, table.fallbackCount,
                "iterating proteinAtoms must not increment fallback counter")

        // Foreign atom bumps the counter.
        Atom foreign = atomAt("C", "ALA", "CA", 0d, 0d, 0d)
        table.get(foreign)
        assertEquals(1L, table.fallbackCount, "foreign atom must bump the counter")
    }

    @Test
    void chargesMatchUnitedAtomTableForStandardResidues() {
        // For any atom whose (residue, name) is in the AMBER table, the cached
        // value must equal the table lookup. Catches a build()-logic regression
        // where the fallback overwrites a real AMBER value.
        Protein protein = Protein.load(PDB)
        PartialChargeTable table = PartialChargeTable.forProtein(protein)

        int matched = 0
        for (Atom atom : protein.proteinAtoms.list) {
            // Use the same residue-code resolver the production build uses — going
            // direct to atom.group.PDBName silently diverges for MSE / modified residues
            // that PdbUtils.correctResidueCode rewrites.
            String res = PdbUtils.getCorrectedAtomResidueCode(atom)
            String name = atom.name
            // PartialChargeTable now uses the united-atom representation (H charges
            // merged into bonded heavy atoms), since PDB files lack explicit Hs.
            double amber = AmberCharges.getUnited(res, name)
            if (Double.isNaN(amber)) continue
            assertEquals(amber, table.get(atom), 1e-9d,
                    "AMBER mismatch on $res/$name")
            matched++
        }
        assertTrue(matched > 0, "no AMBER atoms found in 1fbl — table empty?")
    }
}
