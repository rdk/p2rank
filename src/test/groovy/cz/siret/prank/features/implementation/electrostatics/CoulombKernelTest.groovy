package cz.siret.prank.features.implementation.electrostatics

import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.AtomImpl
import org.biojava.nbio.structure.Element
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.AminoAcidImpl
import org.junit.jupiter.api.Test

import java.util.IdentityHashMap

import static org.junit.jupiter.api.Assertions.*

/**
 * Math correctness on known-geometry fixtures. The kernel is the source of
 * truth for the formulas used by the SAS feature and grid descriptor, so it
 * must be pinned by physics-level tests.
 */
@CompileStatic
class CoulombKernelTest {

    private static final double EPS = 1e-9d

    /** Build a one-atom Atoms with an arbitrary charge. */
    private static Atom atom(double x, double y, double z) {
        AtomImpl a = new AtomImpl()
        a.element = Element.C
        a.name = "C"
        a.x = x; a.y = y; a.z = z
        Group g = new AminoAcidImpl()
        g.setPDBName("ALA")
        a.setGroup(g)
        return a
    }

    private static PartialChargeTable tableOf(Map<Atom, Double> charges) {
        IdentityHashMap<Atom, Double> m = new IdentityHashMap<>()
        m.putAll(charges)
        return PartialChargeTable.forTesting(m)
    }

    @Test
    void singlePositiveChargeAtDistanceR() {
        // Place a +1 charge at (R, 0, 0); probe at origin. Expect:
        //   potential = +1 / R
        //   abs_potential = 1 / R
        //   field magnitude = 1 / R²
        //   positive = 1 / R, negative = 0
        double R = 3.0d
        Atom probe = new Point(0d, 0d, 0d)
        Atom source = atom(R, 0d, 0d)
        Atoms nearby = new Atoms([source])

        PartialChargeTable table = tableOf([(source as Atom): (1.0d as Double)])
        CoulombKernel.Result r = CoulombKernel.accumulate(probe, nearby, table, 0.1d)

        assertEquals(1d/R,    r.potential(),      EPS)
        assertEquals(1d/R,    r.absPotential(),   EPS)
        assertEquals(1d/(R*R), r.fieldMagnitude(), EPS)
        assertEquals(1d/R,    r.positive(),       EPS)
        assertEquals(0d,      r.negative(),       EPS)
    }

    @Test
    void symmetricPlusMinusPairZeroNetPotential() {
        // +1 at (+R, 0, 0), −1 at (−R, 0, 0); probe at origin. Expect:
        //   potential = +1/R + (−1)/R = 0
        //   abs_potential = 2/R
        //   field magnitude = ‖(−1/R²)(+R̂) + (+1/R²)(−R̂)‖
        //         = 2/R² along −x direction → magnitude 2/R²
        //   positive = 1/R, negative = 1/R
        double R = 4.0d
        Atom probe = new Point(0d, 0d, 0d)
        Atom plus = atom(R, 0d, 0d)
        Atom minus = atom(-R, 0d, 0d)
        Atoms nearby = new Atoms([plus, minus])

        PartialChargeTable table = tableOf([
                (plus  as Atom): (+1.0d as Double),
                (minus as Atom): (-1.0d as Double),
        ])
        CoulombKernel.Result r = CoulombKernel.accumulate(probe, nearby, table, 0.1d)

        assertEquals(0d,       r.potential(),      EPS)
        assertEquals(2d/R,     r.absPotential(),   EPS)
        assertEquals(2d/(R*R), r.fieldMagnitude(), EPS)
        assertEquals(1d/R,     r.positive(),       EPS)
        assertEquals(1d/R,     r.negative(),       EPS)
    }

    @Test
    void minRClampsDivisionAtZeroDistance() {
        // Charge directly on top of the probe — without the minR clamp this would
        // be ∞. With minR = 1.0, the result is finite: potential = q / minR.
        double q = 0.5d
        double minR = 1.0d
        Atom probe = new Point(0d, 0d, 0d)
        Atom source = atom(0d, 0d, 0d)
        Atoms nearby = new Atoms([source])

        PartialChargeTable table = tableOf([(source as Atom): (q as Double)])
        CoulombKernel.Result r = CoulombKernel.accumulate(probe, nearby, table, minR)

        // The clamp affects r in q/r but not the dx, dy, dz (which are 0). So
        // field magnitude is exactly 0 (the dot products vanish).
        assertEquals(q / minR, r.potential(), EPS)
        assertEquals(q / minR, r.absPotential(), EPS)
        assertEquals(0d,       r.fieldMagnitude(), EPS, "field magnitude is zero at zero displacement")
    }

    @Test
    void emptyNeighborhoodAllZeros() {
        Atom probe = new Point(0d, 0d, 0d)
        PartialChargeTable table = tableOf([:])
        CoulombKernel.Result r = CoulombKernel.accumulate(probe, new Atoms(), table, 0.1d)

        assertEquals(0d, r.potential(), EPS)
        assertEquals(0d, r.absPotential(), EPS)
        assertEquals(0d, r.fieldMagnitude(), EPS)
        assertEquals(0d, r.positive(), EPS)
        assertEquals(0d, r.negative(), EPS)
    }
}
