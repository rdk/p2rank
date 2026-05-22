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
 * Tests the Gaussian weighting math: kernel value at known distances, summing
 * across atoms, and the 4σ cutoff. These are the numeric facts that, if broken,
 * silently produce wrong scores — exactly what unit tests should catch.
 */
@Isolated
@ResourceLock("Params")
@CompileStatic
class VolsiteSmoothGridPointDescriptorTest {

    private static final double DELTA = 1e-9
    /**
     * Sigma value the tests build their distance literals around. Pinned in
     * {@link #setSigma} so other tests mutating {@code Params.inst.pocket_grid_volsite_sigma}
     * can't silently shift our expectations.
     */
    private static final double SIGMA = 2.0d

    private static final int AROMATIC = 0, CATION = 1, ANION = 2,
                             HYDROPHOBIC = 3, ACCEPTOR = 4, DONOR = 5

    private double savedSigma

    @BeforeEach
    void setSigma() {
        savedSigma = Params.inst.pocket_grid_volsite_sigma
        Params.inst.pocket_grid_volsite_sigma = SIGMA
    }

    @AfterEach
    void restoreSigma() {
        // Restore the global Params singleton so test classes that run after this
        // one (and that read pocket_grid_volsite_sigma without pinning it) aren't
        // affected by our pin.
        Params.inst.pocket_grid_volsite_sigma = savedSigma
    }

    // Signature matches VolsiteGridPointDescriptorTest.atomAt (element, resName, atomName, x, y, z)
    // — both tests build atoms the same way so calls don't get swapped between files.
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
    void weightAtZeroDistanceIsOne() {
        // exp(0) = 1.0 exactly. Atom name "C" is hydrophobic.
        Atom c = atomAt("C", "ALA", "C", 0d, 0d, 0d)
        double[] out = computeArr(new VolsiteSmoothGridPointDescriptor(),
                ctxAt(0d, 0d, 0d, new Atoms([c])))
        assertEquals(1.0d, out[HYDROPHOBIC], DELTA)
    }

    @Test
    void weightAtSigmaMatchesGaussianFormula() {
        // At distance r = σ, weight = exp(-r²/(2σ²)) = exp(-1/2) ≈ 0.6065.
        double sigma = SIGMA
        Atom c = atomAt("C", "ALA", "C", sigma, 0d, 0d)
        double[] out = computeArr(new VolsiteSmoothGridPointDescriptor(),
                ctxAt(0d, 0d, 0d, new Atoms([c])))
        assertEquals(Math.exp(-0.5d), out[HYDROPHOBIC], DELTA)
    }

    @Test
    void weightsFromMultipleAtomsOfSameTypeSum() {
        // Two hydrophobic atoms at distance σ each → sum = 2 × exp(-0.5).
        double sigma = SIGMA
        Atoms protein = new Atoms([
                atomAt("C", "ALA", "C", sigma, 0d, 0d),
                atomAt("C", "ALA", "C", 0d, sigma, 0d),
        ])
        double[] out = computeArr(new VolsiteSmoothGridPointDescriptor(),
                ctxAt(0d, 0d, 0d, protein))
        assertEquals(2d * Math.exp(-0.5d), out[HYDROPHOBIC], DELTA)
    }

    @Test
    void weightAtExactCutoffEqualsExpMinusEight() {
        // The cutoff is 4σ. cutoutSphere is inclusive (dist <= radius), so an atom AT
        // exactly 4σ IS included and contributes exp(-(4σ)²/(2σ²)) = exp(-8) ≈ 3.354e-4.
        // Pins the boundary semantic (cutoff is inclusive, not strict).
        double sigma = SIGMA
        Atom c = atomAt("C", "ALA", "C", 4d * sigma, 0d, 0d)
        double[] out = computeArr(new VolsiteSmoothGridPointDescriptor(),
                ctxAt(0d, 0d, 0d, new Atoms([c])))
        assertEquals(Math.exp(-8d), out[HYDROPHOBIC], DELTA)
    }

    @Test
    void atomBeyondCutoffContributesZero() {
        // 4σ is the hard cutoff (cutoutSphere is the gate). At 5σ the atom isn't even
        // in the kdtree result. Zero contribution.
        double sigma = SIGMA
        Atom c = atomAt("C", "ALA", "C", 5d * sigma, 0d, 0d)
        double[] out = computeArr(new VolsiteSmoothGridPointDescriptor(),
                ctxAt(0d, 0d, 0d, new Atoms([c])))
        assertEquals(0d, out[HYDROPHOBIC], DELTA)
    }

}
