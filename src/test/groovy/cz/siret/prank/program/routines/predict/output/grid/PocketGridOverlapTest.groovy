package cz.siret.prank.program.routines.predict.output.grid

import cz.siret.prank.program.routines.predict.output.grid.fill.FillKnobs
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.AtomImpl
import org.biojava.nbio.structure.Element
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * SCAFFOLD. Pins the inter-pocket grid-point overlap behaviour, so we have a
 * regression anchor for the {@code morph_closing} over-dilation issue surfaced
 * by {@code prank analyze pocket-grid-overlap}.
 *
 * <p>Background: each pocket's SAS points are partitioned one-per-pocket, so the
 * RAW shells (fill=none) can only overlap in a thin interface band where two
 * pockets' SAS clusters approach within {@code 2 * assignCutoff}. Substantial
 * overlap -- up to one pocket being a near-subset of another -- is introduced by
 * {@code morph_closing}, which dilates each pocket's shell independently against
 * the shared lattice envelope.
 *
 * <p>The synthetic tests below are self-contained and demonstrate the mechanism
 * on a hand-built lattice. Real-protein regression cases (selected from the
 * {@code analyze pocket-grid-overlap} shortlist) live in the heavier
 * {@code PocketGridOverlapIntegrationTest}, which runs the full prediction
 * pipeline.
 */
@CompileStatic
class PocketGridOverlapTest {

    private static class TestPocket extends Pocket {}

    private static Atom carbonAt(double x, double y, double z) {
        AtomImpl a = new AtomImpl()
        a.element = Element.C
        a.name = "C"
        a.x = x; a.y = y; a.z = z
        return a
    }

    private static Atoms sasAt(double... xyz) {
        List<Atom> pts = new ArrayList<>()
        for (int i = 0; i < xyz.length; i += 3) {
            pts.add(new Point(xyz[i], xyz[i + 1], xyz[i + 2]) as Atom)
        }
        return new Atoms(pts)
    }

    private static Protein proteinWith(Atoms protAtoms) {
        Protein p = new Protein()
        p.proteinAtoms = protAtoms
        return p
    }

    private static TestPocket pocket(int rank, Atoms sas) {
        TestPocket p = new TestPocket()
        p.rank = rank
        p.sasPoints = sas
        return p
    }

    // NB: BitSet intersection uses Groovy's `&` operator, which returns a new
    // BitSet. Do NOT call `inter.and(other)` here: under @CompileStatic that binds
    // to Groovy's DefaultGroovyMethods.and(BitSet,BitSet), which RETURNS the
    // intersection instead of mutating in place -- a silent no-op that makes every
    // "overlap" equal the receiver's cardinality.

    /** |A∩B| / min(|A|,|B|) -- ~1.0 means the smaller pocket is engulfed by the larger. */
    private static double containment(PocketGrid g, int rankA, int rankB) {
        BitSet a = g.indicesForPocket(rankA)
        BitSet b = g.indicesForPocket(rankB)
        int sa = a.cardinality(), sb = b.cardinality()
        if (sa == 0 || sb == 0) return 0d
        return (a & b).cardinality() / (double) Math.min(sa, sb)
    }

    private static int overlap(PocketGrid g, int rankA, int rankB) {
        return (g.indicesForPocket(rankA) & g.indicesForPocket(rankB)).cardinality()
    }

    // morph_closing with min_neighbors=4, max_iters=10
    private static PocketGridConfig morphConfig() {
        new PocketGridConfig(1.0d, 6.0d, 0.5d, 2.5d, 'kdtree', 'morph_closing', new FillKnobs.Morph(4, 10))
    }

    // identical geometry knobs, fill disabled -> raw shells only
    private static PocketGridConfig noFillConfig() {
        new PocketGridConfig(1.0d, 6.0d, 0.5d, 2.5d, 'kdtree', 'none', new FillKnobs.None())
    }

    // true dilate-then-erode closing, symmetric radius
    private static PocketGridConfig closingConfig(int radius) {
        new PocketGridConfig(1.0d, 6.0d, 0.5d, 2.5d, 'kdtree', 'closing', FillKnobs.Closing.symmetric(radius))
    }

    /**
     * Two pockets whose SAS points are 7 Å apart -> their raw shells (cutoff 2.5 Å)
     * are disjoint, but they sit in one contiguous lattice envelope. Asserts that
     * morph_closing introduces large overlap where the raw shell had effectively
     * none -- i.e. the overlap is a fill artefact, not a property of the assignment.
     *
     * NOTE: thresholds here are illustrative placeholders for the scaffold; tighten
     * them once the real worst-case numbers are in from the dataset run.
     */
    @Test
    void morphClosingInflatesOverlapThatRawShellDoesNot() {
        // Protein atom parked far away so nothing near the pocket line is excluded ->
        // the inter-pocket region is open solvent and the dilation can bridge it.
        Protein protein = proteinWith(new Atoms([carbonAt(0d, -30d, 0d)]))

        // Pocket 1 ("big"): small SAS cluster around the origin.
        TestPocket p1 = pocket(1, sasAt(-1d, 0d, 0d, 0d, 0d, 0d, 1d, 0d, 0d))
        // Pocket 2 ("small"): single SAS point 7 Å away -> disjoint raw shell.
        TestPocket p2 = pocket(2, sasAt(7d, 0d, 0d))
        List<Pocket> pockets = [p1, p2] as List<Pocket>

        PocketGrid raw   = PocketGridBuilder.build(protein, pockets, noFillConfig())
        PocketGrid morph = PocketGridBuilder.build(protein, pockets, morphConfig())

        // Raw shells of pockets whose SAS clusters are 7 Å apart are fully disjoint.
        assertEquals(0, overlap(raw, 1, 2),
                "raw shells of 7 Å-separated pockets should be disjoint")
        assertEquals(0.0d, containment(raw, 1, 2), 1e-9d)

        // morph_closing (min_neighbors=4, max_iters=10) dilates each shell across the
        // shared envelope until the smaller pocket is almost entirely contained in the
        // larger one -- overlap created purely by the fill stage, not the assignment.
        // Observed: overlap=1476, containment=0.97. Thresholds carry margin.
        assertTrue(overlap(morph, 1, 2) > 500,
                "morph_closing should create large overlap (got ${overlap(morph, 1, 2)})")
        assertTrue(containment(morph, 1, 2) > 0.9d,
                "morph_closing should engulf the smaller pocket (containment " +
                "${containment(morph, 1, 2)}, expected > 0.9)")
    }

    /**
     * Sanity floor: when the two pockets are far apart AND the envelope between them
     * is broken (no protein bridge of grid points), even morph_closing must not
     * manufacture overlap. Guards against a future "fill leaks across empty space"
     * regression. Here the SAS points are 20 Å apart -> two separate envelopes.
     */
    @Test
    void morphClosingDoesNotBridgeDisconnectedEnvelopes() {
        Protein protein = proteinWith(new Atoms([carbonAt(0d, -30d, 0d)]))
        TestPocket p1 = pocket(1, sasAt(0d, 0d, 0d))
        TestPocket p2 = pocket(2, sasAt(20d, 0d, 0d))   // > 2 * maxDist apart
        List<Pocket> pockets = [p1, p2] as List<Pocket>

        PocketGrid morph = PocketGridBuilder.build(protein, pockets, morphConfig())
        assertEquals(0, overlap(morph, 1, 2),
                "far-apart pockets with no connecting grid points must not overlap")
    }

    /**
     * PROTOTYPE-FIX regression: the true dilate-then-erode {@code closing} filler
     * must NOT bridge pockets that {@code morph_closing} engulfs. Same open,
     * contiguous envelope as {@link #morphClosingInflatesOverlapThatRawShellDoesNot},
     * but the two pockets are 10 Å apart. {@code morph_closing} dilates across the
     * gap and engulfs the smaller pocket; {@code closing} dilates then erodes, so
     * the advance into the open inter-pocket region is peeled back and the pockets
     * stay disjoint (overlap 0, like the raw shell).
     *
     * <p>(On a convex synthetic blob there is nothing to fill, so {@code closing}
     * leaves the shell unchanged; its fill benefit shows on real concave pocket
     * cavities -- see {@code PocketGridOverlapIntegrationTest} and the
     * {@code analyze pocket-grid-overlap} fill-volume numbers.)
     */
    @Test
    void closingFillDoesNotBridgePocketsThatMorphClosingEngulfs() {
        Protein protein = proteinWith(new Atoms([carbonAt(0d, -30d, 0d)]))
        TestPocket p1 = pocket(1, sasAt(-1d, 0d, 0d, 0d, 0d, 0d, 1d, 0d, 0d))
        TestPocket p2 = pocket(2, sasAt(10d, 0d, 0d))   // open gap, one shared envelope
        List<Pocket> pockets = [p1, p2] as List<Pocket>

        PocketGrid morph   = PocketGridBuilder.build(protein, pockets, morphConfig())
        PocketGrid closing = PocketGridBuilder.build(protein, pockets, closingConfig(2))

        assertTrue(containment(morph, 1, 2) > 0.5d,
                "morph_closing should bridge the open gap and engulf " +
                "(containment ${containment(morph, 1, 2)})")
        assertEquals(0, overlap(closing, 1, 2),
                "true closing must not bridge separated pockets across an open gap")
    }

}
