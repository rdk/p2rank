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
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests the Gaussian weighting math: kernel value at known distances, summing
 * across atoms, and the 4σ cutoff. These are the numeric facts that, if broken,
 * silently produce wrong scores — exactly what unit tests should catch.
 */
@CompileStatic
class VolsiteSmoothGridPointDescriptorTest {

    private static final double DELTA = 1e-9

    private static final int AROMATIC = 0, CATION = 1, ANION = 2,
                             HYDROPHOBIC = 3, ACCEPTOR = 4, DONOR = 5

    private static Atom atomAt(String atomName, String resName, double x, double y, double z) {
        AtomImpl a = new AtomImpl()
        a.element = Element.C
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
    void weightAtZeroDistanceIsOne() {
        // exp(0) = 1.0 exactly. Atom name "C" is hydrophobic.
        Atom c = atomAt("C", "ALA", 0d, 0d, 0d)
        double[] out = new VolsiteSmoothGridPointDescriptor().compute(
                ctxAt(0d, 0d, 0d, new Atoms([c])))
        assertEquals(1.0d, out[HYDROPHOBIC], DELTA)
    }

    @Test
    void weightAtSigmaMatchesGaussianFormula() {
        // At distance r = σ, weight = exp(-r²/(2σ²)) = exp(-1/2) ≈ 0.6065.
        double sigma = Params.inst.pocket_grid_volsite_sigma
        Atom c = atomAt("C", "ALA", sigma, 0d, 0d)
        double[] out = new VolsiteSmoothGridPointDescriptor().compute(
                ctxAt(0d, 0d, 0d, new Atoms([c])))
        assertEquals(Math.exp(-0.5d), out[HYDROPHOBIC], DELTA)
    }

    @Test
    void weightsFromMultipleAtomsOfSameTypeSum() {
        // Two hydrophobic atoms at distance σ each → sum = 2 × exp(-0.5).
        double sigma = Params.inst.pocket_grid_volsite_sigma
        Atoms protein = new Atoms([
                atomAt("C", "ALA", sigma, 0d, 0d),
                atomAt("C", "ALA", 0d, sigma, 0d),
        ])
        double[] out = new VolsiteSmoothGridPointDescriptor().compute(
                ctxAt(0d, 0d, 0d, protein))
        assertEquals(2d * Math.exp(-0.5d), out[HYDROPHOBIC], DELTA)
    }

    @Test
    void atomBeyondCutoffContributesZero() {
        // 4σ is the hard cutoff (cutoutSphere is the gate). At 5σ the atom isn't even
        // in the kdtree result. Zero contribution.
        double sigma = Params.inst.pocket_grid_volsite_sigma
        Atom c = atomAt("C", "ALA", 5d * sigma, 0d, 0d)
        double[] out = new VolsiteSmoothGridPointDescriptor().compute(
                ctxAt(0d, 0d, 0d, new Atoms([c])))
        assertEquals(0d, out[HYDROPHOBIC], DELTA)
    }

}
