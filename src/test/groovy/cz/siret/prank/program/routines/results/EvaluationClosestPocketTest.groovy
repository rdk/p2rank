package cz.siret.prank.program.routines.results

import cz.siret.prank.domain.BindingSite
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.SiteCenterMethod
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.prediction.pockets.PrankPocket
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static org.junit.jupiter.api.Assertions.*

/**
 * Regression test for the {@code site_eval_sas_pts_as_atoms} flag in
 * {@link Evaluation#closestPocket}. Before the fix, closestPocket measured
 * against {@code site.atoms} unconditionally — so the reported
 * {@code closestPocketDist} disagreed with the DCA criterion (which honors
 * the flag) whenever the flag was enabled.
 *
 * Uses a tiny in-process BindingSite mock so we can control atoms and
 * sasPoints independently without setting up a full protein pipeline.
 */
@Isolated
@ResourceLock("Params")
@CompileStatic
class EvaluationClosestPocketTest {

    static boolean savedFlag

    @BeforeAll
    static void snapshot() { savedFlag = Params.inst.site_eval_sas_pts_as_atoms }

    @AfterAll
    static void restore() { Params.inst.site_eval_sas_pts_as_atoms = savedFlag }

    @Test
    void closestPocketUsesSitesAtomsWhenFlagOff() {
        Params.inst.site_eval_sas_pts_as_atoms = false
        Pocket res = Evaluation.closestPocket(twoLocSite(), [nearAtoms(), nearSas()])
        assertEquals("nearAtoms", res.name)
    }

    @Test
    void closestPocketUsesSitesSasWhenFlagOn() {
        Params.inst.site_eval_sas_pts_as_atoms = true
        Pocket res = Evaluation.closestPocket(twoLocSite(), [nearAtoms(), nearSas()])
        assertEquals("nearSas", res.name)
    }

    // --- fixtures ---

    /** Site whose atoms are at the origin and sasPoints 30 Å away on +x. */
    private static BindingSite twoLocSite() {
        Atom atomsPt = new Point(0d, 0d, 0d) as Atom
        Atom sasPt   = new Point(30d, 0d, 0d) as Atom
        return new MockSite(new Atoms([atomsPt]), new Atoms([sasPt]))
    }

    private static Pocket nearAtoms() {
        Pocket p = new PrankPocket(new Point(0.1d, 0d, 0d) as Atom, 1d, new Atoms(), [])
        p.name = "nearAtoms"; return p
    }

    private static Pocket nearSas() {
        Pocket p = new PrankPocket(new Point(30d, 0.1d, 0d) as Atom, 1d, new Atoms(), [])
        p.name = "nearSas"; return p
    }

    /** Minimal BindingSite — only the fields closestPocket touches are populated. */
    @CompileStatic
    private static final class MockSite implements BindingSite {
        private final Atoms atoms
        private Atoms sasPoints
        private Pocket predictedPocket

        MockSite(Atoms atoms, Atoms sasPoints) {
            this.atoms = atoms
            this.sasPoints = sasPoints
        }

        @Override Atoms getAtoms() { atoms }
        @Override Atom  getCentroid() { atoms.centerOfMass }
        @Override Atom  getCenterForEval() { atoms.centerOfMass }
        @Override Atom  getCenterForMethod(SiteCenterMethod m) { atoms.centerOfMass }
        @Override Atoms getSasPoints() { sasPoints }
        @Override void  setSasPoints(Atoms s) { this.sasPoints = s }
        @Override String getLabel() { "mock" }
        @Override Pocket getPredictedPocket() { predictedPocket }
        @Override void setPredictedPocket(Pocket p) { this.predictedPocket = p }
    }
}
