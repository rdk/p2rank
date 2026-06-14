package cz.siret.prank.features.implementation.contactres

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.Residues
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.program.params.Params
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.AtomImpl
import org.biojava.nbio.structure.Element
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.HetatomImpl
import org.biojava.nbio.structure.ResidueNumber
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock

import static org.junit.jupiter.api.Assertions.*

/**
 * Regression test for the {@code crpos} feature and modified / HETATM residues.
 *
 * A modified residue (e.g. LLP, MSE) can have a 3-letter code that resolves to a standard
 * amino acid via {@link Residue#getAa} (corrected code) while its BioJava {@link Group} is a
 * {@link HetatomImpl}, not an {@link org.biojava.nbio.structure.AminoAcid}, so
 * {@link Residue#getAminoAcid} returns {@code null}. {@code crpos} admits contact residues on
 * {@code getAa() != null} and previously dereferenced {@code aminoAcid.getCA()} unconditionally,
 * which threw a NPE for exactly these residues (increasingly common with the -cofactors / AA-mapping work).
 */
@ResourceLock("Params")
class ContactResiduesPositionFeatureTest {

    private Params savedParams

    @BeforeEach
    void setUp() {
        savedParams = Params.INSTANCE
        Params.INSTANCE = new Params()   // default feat_crang_contact_dist = 4.0
    }

    @AfterEach
    void tearDown() {
        Params.INSTANCE = savedParams
    }

    /** Modified/HETATM residue whose corrected code resolves to a standard AA but which is NOT a BioJava AminoAcid. */
    private static Residue mappedHetatmResidue(double x, double y, double z) {
        AtomImpl a = new AtomImpl()
        a.setElement(Element.C)
        a.setName("CB")
        a.setX(x); a.setY(y); a.setZ(z)

        Group g = new HetatomImpl()                          // NOT AminoAcidImpl => getAminoAcid() == null
        g.setPDBName("ALA")                                  // corrected code resolves to AA.ALA
        g.setResidueNumber(new ResidueNumber("A", 1 as Integer, null))
        g.addAtom(a)
        a.setGroup(g)

        return Residue.fromGroup(g)
    }

    @Test
    void crposToleratesMappedHetatmResidueWithoutAminoAcid() {
        Residue mod = mappedHetatmResidue(3d, 0d, 0d)

        // precondition: this residue reproduces the exact triggering condition
        assertNotNull(mod.aa, "corrected code must resolve to a standard AA")
        assertNull(mod.aminoAcid, "group must not be a BioJava AminoAcid")

        Atoms residueAtoms = mod.atoms
        Protein protein = new Protein() {
            @Override Residues getResidues() { Residues.of([mod]) }
            @Override Atoms getExposedAtoms() { residueAtoms }
        }

        Atom sasPoint = new Point(0d, 0d, 0d)                // within feat_crang_contact_dist (4.0) of the residue
        def feature = new ContactResiduesPositionFeature()
        def ctx = new SasFeatureCalculationContext(protein, residueAtoms, null)

        // Before the fix this threw NPE at `closestResOfType.aminoAcid.getCA()`.
        double[] vect = feature.calculateForSasPoint(sasPoint, ctx)

        int base = feature.header.indexOf("crpos.ala.count")
        assertTrue(base >= 0, "crpos.ala.count column must exist")
        double count      = vect[base]
        double distca     = vect[base + 1]
        double distcenter = vect[base + 3]

        assertEquals(1d, count, 0d, "the mapped HETATM residue must still be counted")
        // CA is unavailable (aminoAcid == null) => distca falls back to distcenter, not the 20.0 'no-contact' default
        assertEquals(distcenter, distca, 1e-9, "distca must fall back to distcenter when the residue has no CA")
        assertEquals(3d, distca, 1e-9, "fallback distance equals SAS-point<->residue-center distance")
        assertTrue(vect.every { Double.isFinite(it) }, "all crpos values must be finite")
    }
}
