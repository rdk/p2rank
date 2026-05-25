package cz.siret.prank.features.implementation.physics

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.ResidueChain
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static org.junit.jupiter.api.Assertions.*

/**
 * Smoke integration test: load a real PDB (1t7qa) and verify both per-protein
 * computations (AnmModel + ContactGraph) produce finite, reasonable values
 * across every residue. Also verifies the per-protein cache: a second
 * invocation returns the same instance (no recomputation).
 */
@Isolated
@ResourceLock("Params")
@CompileStatic
class PhysicsFeaturesIT {

    static final String PDB_1T7QA = "distro/test_data/liganated/1t7qa.pdb"
    static final String PDB_11AS = "src/test/resources/data/11as.pdb"

    static Params savedParams

    @BeforeAll
    static void setup() {
        // Snapshot the global Params so we can mutate freely.
        savedParams = Params.INSTANCE
        Params.INSTANCE = new Params()
    }

    @AfterAll
    static void tearDown() {
        Params.INSTANCE = savedParams
    }

    private static Protein load1t7qa() {
        File f = new File(PDB_1T7QA)
        assertTrue(f.exists(), "expected test PDB at $PDB_1T7QA")
        return Protein.load(PDB_1T7QA, new LoaderParams())
    }

    @Test
    void anmModelProducesFiniteValuesOnRealProtein() {
        Protein p = load1t7qa()
        int n = p.residues.count
        assertTrue(n > 50, "1t7qa should have > 50 residues (got $n)")

        AnmModel m = AnmModel.getOrCompute(p, Params.INSTANCE)
        assertNotNull(m)

        int withCa = 0
        int withoutCa = 0
        double maxMsf = 0d
        for (Residue r : p.residues) {
            double s = m.sensorFor(r)
            double e = m.effectivenessFor(r)
            double f = m.msfFor(r)
            assertTrue(Double.isFinite(s), "sensor non-finite at $r.key")
            assertTrue(Double.isFinite(e), "effectiveness non-finite at $r.key")
            assertTrue(Double.isFinite(f), "msf non-finite at $r.key")
            assertTrue(s >= 0d, "sensor negative at $r.key")
            assertTrue(e >= 0d, "effectiveness negative at $r.key")
            assertTrue(f >= 0d, "msf negative at $r.key")
            if (r.aminoAcid?.getCA() != null) {
                withCa++
                if (f > 0d) maxMsf = Math.max(maxMsf, f)
            } else {
                withoutCa++
                // residues without Cα should have zero feature values
                assertEquals(0d, s, 0d, "sensor should be 0 for residue without Cα ($r.key)")
                assertEquals(0d, e, 0d, "effectiveness should be 0 for residue without Cα ($r.key)")
                assertEquals(0d, f, 0d, "msf should be 0 for residue without Cα ($r.key)")
            }
        }
        assertTrue(withCa > 50, "expected many residues with Cα (got $withCa)")
        assertTrue(maxMsf > 0d, "some MSF should be strictly positive on a real protein")
    }

    @Test
    void contactGraphProducesReasonableValuesOnRealProtein() {
        Protein p = load1t7qa()
        ContactGraph g = ContactGraph.getOrCompute(p, Params.INSTANCE)
        assertNotNull(g)

        double maxBet = 0d
        double maxClose = 0d
        double maxDeg = 0d
        int nonzeroDeg = 0
        for (Residue r : p.residues) {
            double b = g.betweennessFor(r)
            double c = g.closenessFor(r)
            double d = g.degreeFor(r)
            assertTrue(Double.isFinite(b))
            assertTrue(Double.isFinite(c))
            assertTrue(Double.isFinite(d))
            assertTrue(b >= 0d && b <= 1d, "betweenness out of [0,1] at $r.key: $b")
            assertTrue(c >= 0d && c <= 1d, "closeness out of [0,1] at $r.key: $c")
            assertTrue(d >= 0d, "degree negative at $r.key")
            if (d > 0d) nonzeroDeg++
            maxBet = Math.max(maxBet, b)
            maxClose = Math.max(maxClose, c)
            maxDeg = Math.max(maxDeg, d)
        }
        assertTrue(nonzeroDeg > 20, "most residues should have at least one contact (got $nonzeroDeg)")
        assertTrue(maxBet > 0d, "some residue should have non-zero betweenness")
        assertTrue(maxClose > 0d, "some residue should have non-zero closeness")
        assertTrue(maxDeg >= 3d, "a typical protein has residues with several contacts (got max degree $maxDeg)")
    }

    @Test
    void contactGraphPerChainOnMultiChainProtein() {
        File f = new File(PDB_11AS)
        assertTrue(f.exists(), "expected test PDB at $PDB_11AS")
        Protein p = Protein.load(PDB_11AS, new LoaderParams())

        assertTrue(p.residueChains.size() >= 2,
                "11as should have at least 2 chains (got ${p.residueChains.size()})")

        ContactGraph g = ContactGraph.getOrCompute(p, Params.INSTANCE)
        assertNotNull(g)

        for (Residue r : p.residues) {
            double b = g.betweennessFor(r)
            double c = g.closenessFor(r)
            double d = g.degreeFor(r)
            assertTrue(Double.isFinite(b), "betweenness non-finite at $r.key")
            assertTrue(Double.isFinite(c), "closeness non-finite at $r.key")
            assertTrue(Double.isFinite(d), "degree non-finite at $r.key")
            assertTrue(b >= 0d && b <= 1d, "betweenness out of [0,1] at $r.key: $b")
            assertTrue(c >= 0d && c <= 1d, "closeness out of [0,1] at $r.key: $c")
            assertTrue(d >= 0d, "degree negative at $r.key")
        }

        ResidueChain chainA = p.getResidueChain("A")
        ResidueChain chainB = p.getResidueChain("B")
        assertNotNull(chainA, "expected chain A")
        assertNotNull(chainB, "expected chain B")

        double maxBetA = 0d, maxBetB = 0d
        for (Residue r : chainA.residues) maxBetA = Math.max(maxBetA, g.betweennessFor(r))
        for (Residue r : chainB.residues) maxBetB = Math.max(maxBetB, g.betweennessFor(r))

        assertTrue(maxBetA > 0d, "chain A should have non-zero betweenness")
        assertTrue(maxBetB > 0d, "chain B should have non-zero betweenness")

        double relDiff = Math.abs(maxBetA - maxBetB) / Math.max(maxBetA, maxBetB)
        assertTrue(relDiff < 0.5,
                "homodimer chains should have similar max betweenness (A=$maxBetA, B=$maxBetB, relDiff=$relDiff)")

        ContactGraph g2 = ContactGraph.getOrCompute(p, Params.INSTANCE)
        assertSame(g, g2, "ContactGraph should be cached per protein")
    }

    @Test
    void cacheReturnsSameInstanceOnSecondCall() {
        Protein p = load1t7qa()
        AnmModel a1 = AnmModel.getOrCompute(p, Params.INSTANCE)
        AnmModel a2 = AnmModel.getOrCompute(p, Params.INSTANCE)
        assertSame(a1, a2, "AnmModel should be cached per protein")

        ContactGraph g1 = ContactGraph.getOrCompute(p, Params.INSTANCE)
        ContactGraph g2 = ContactGraph.getOrCompute(p, Params.INSTANCE)
        assertSame(g1, g2, "ContactGraph should be cached per protein")
    }
}
