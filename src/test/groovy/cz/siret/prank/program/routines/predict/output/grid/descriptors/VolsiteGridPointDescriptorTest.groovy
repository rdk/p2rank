package cz.siret.prank.program.routines.predict.output.grid.descriptors

import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.AtomImpl
import org.biojava.nbio.structure.Element
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.AminoAcidImpl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static cz.siret.prank.program.routines.predict.output.grid.descriptors.PocketGridPointDescriptors.computeArr
import static org.junit.jupiter.api.Assertions.*

/**
 * Behavioural tests for the indicator volsite descriptor. We don't test every
 * pharmacophore branch (that's {@code VolSitePharmacophore}'s domain) — we
 * verify that the descriptor correctly aggregates per-atom flags into the
 * 6-column row and honors the cutoff radius.
 *
 * <p>Column order (matches {@code VolSitePharmacophore.COLUMN_NAMES}):
 * aromatic, cation, anion, hydrophobic, acceptor, donor.
 */
@Isolated
@ResourceLock("Params")
@CompileStatic
class VolsiteGridPointDescriptorTest {

    private static final int AROMATIC = 0, CATION = 1, ANION = 2,
                             HYDROPHOBIC = 3, ACCEPTOR = 4, DONOR = 5

    /** Radius the boundary tests build their atom distances around. */
    private static final double RADIUS = 4.0d

    private double savedRadius

    @BeforeEach
    void setRadius() {
        savedRadius = Params.inst.pocket_grid_volsite_radius
        Params.inst.pocket_grid_volsite_radius = RADIUS
    }

    @AfterEach
    void restoreRadius() {
        Params.inst.pocket_grid_volsite_radius = savedRadius
    }

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

    private static PocketGridPointContext ctxAt(double x, double y, double z, Atoms proteinAtoms) {
        Protein p = new Protein()
        p.proteinAtoms = proteinAtoms
        return new PocketGridPointContext(0, new Point(x, y, z), 0, null, p, null)
    }

    @Test
    void singleHydrophobicAtomNearbySetsOnlyHydrophobic() {
        // Atom name "C" + any residue → hydrophobic (first branch in VolSitePharmacophore).
        Atom c = atomAt("C", "ALA", "C", 1d, 0d, 0d)  // 1 Å from grid point — well inside default 4 Å
        double[] out = computeArr(new VolsiteGridPointDescriptor(),
                ctxAt(0d, 0d, 0d, new Atoms([c])))

        assertEquals(1d, out[HYDROPHOBIC])
        for (int i = 0; i < 6; i++) if (i != HYDROPHOBIC) assertEquals(0d, out[i], 0d, "col $i")
    }

    @Test
    void atomOutsideRadiusContributesNothing() {
        // Radius is pinned to 4.0 in @BeforeEach; place a hydrophobic atom at 5 Å.
        Atom c = atomAt("C", "ALA", "C", 5d, 0d, 0d)
        double[] out = computeArr(new VolsiteGridPointDescriptor(),
                ctxAt(0d, 0d, 0d, new Atoms([c])))

        for (int i = 0; i < 6; i++) assertEquals(0d, out[i], 0d, "col $i")
    }

    @Test
    void mixedNearbyAtomsSetMultipleIndependentFlags() {
        // One donor (N backbone) + one anion (OD1 in ASP) + one cation (ZN — name-only rule).
        // All within 4 Å of origin. Three indicators should fire; the other three should not.
        Atoms protein = new Atoms([
                atomAt("N", "ALA", "N",   1d, 0d, 0d),
                atomAt("O", "ASP", "OD1", 0d, 1d, 0d),
                atomAt("ZN", "ZN", "ZN",  0d, 0d, 1d),
        ])
        double[] out = computeArr(new VolsiteGridPointDescriptor(),
                ctxAt(0d, 0d, 0d, protein))

        assertEquals(1d, out[DONOR])
        assertEquals(1d, out[ANION])
        assertEquals(1d, out[CATION])
        assertEquals(0d, out[AROMATIC])
        assertEquals(0d, out[HYDROPHOBIC])
        assertEquals(0d, out[ACCEPTOR])
    }

    @Test
    void emptyNeighborhoodAllZeros() {
        double[] out = computeArr(new VolsiteGridPointDescriptor(),
                ctxAt(0d, 0d, 0d, new Atoms()))
        for (int i = 0; i < 6; i++) assertEquals(0d, out[i], 0d)
    }

    @Test
    void singleAtomWithTwoPharmacophoreFlagsLightsBothColumns() {
        // ND1 in HIS sets BOTH donor and acceptor (VolSitePharmacophore.java).
        // The 6-flag aggregator's "two flags from one atom" path is non-trivial;
        // a regression that overwrites one with the other would slip through if
        // every other test only exercises one-flag atoms.
        Atom nd1His = atomAt("N", "HIS", "ND1", 1d, 0d, 0d)
        double[] out = computeArr(new VolsiteGridPointDescriptor(),
                ctxAt(0d, 0d, 0d, new Atoms([nd1His])))

        assertEquals(1d, out[DONOR])
        assertEquals(1d, out[ACCEPTOR])
        // The other four must remain off.
        assertEquals(0d, out[AROMATIC])
        assertEquals(0d, out[CATION])
        assertEquals(0d, out[ANION])
        assertEquals(0d, out[HYDROPHOBIC])
    }

}
