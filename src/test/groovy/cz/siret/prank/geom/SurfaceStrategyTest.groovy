package cz.siret.prank.geom

import cz.siret.prank.domain.Protein
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.CdkUtils
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.Test
import org.openscience.cdk.interfaces.IAtomContainer

import static org.junit.jupiter.api.Assertions.*

/**
 * Regression tests for the pluggable {@link SurfaceStrategy}: the PACKED strategy (flat store + zero-copy
 * delivery) must produce a surface identical to the FASTER strategy at the p2rank level, and the
 * strategy resolution (incl. the deprecated use_optimized_surface alias) must behave as specified.
 */
@CompileStatic
class SurfaceStrategyTest {

    static final String PDB = "src/test/resources/data/2src.pdb"

    private static IAtomContainer load() {
        Protein protein = Protein.load(PDB)
        return CdkUtils.toAtomContainer(protein.proteinAtoms)
    }

    @Test
    void packedMatchesFasterExactly() {
        IAtomContainer c = load()
        double sr = 1.6
        for (int tess in [2, 3, 4]) {
            SurfaceStrategy.RawSurface faster = SurfaceStrategy.FASTER.compute(c, sr, tess)
            SurfaceStrategy.RawSurface packed = SurfaceStrategy.PACKED.compute(c, sr, tess)

            assertEquals(faster.totalSurfaceArea, packed.totalSurfaceArea, 0.0d,
                    "total area must match exactly (tess=$tess)")
            assertEquals(faster.points.count, packed.points.count,
                    "point count must match (tess=$tess)")

            List<Atom> fp = faster.points.list
            List<Atom> pp = packed.points.list
            for (int i = 0; i < fp.size(); i++) {
                assertEquals(fp[i].x, pp[i].x, 0.0d, "x[$i] tess=$tess")
                assertEquals(fp[i].y, pp[i].y, 0.0d, "y[$i] tess=$tess")
                assertEquals(fp[i].z, pp[i].z, 0.0d, "z[$i] tess=$tess")
            }
        }
    }

    @Test
    void packedDistinctV3MatchesV2Exactly() {
        IAtomContainer c = load()
        double sr = 1.6
        for (int tess in [2, 3, 4]) {
            SurfaceStrategy.RawSurface v2 = SurfaceStrategy.PACKED_DISTINCT_V2.compute(c, sr, tess)
            SurfaceStrategy.RawSurface v3 = SurfaceStrategy.PACKED_DISTINCT_V3.compute(c, sr, tess)

            assertEquals(v2.totalSurfaceArea, v3.totalSurfaceArea, 0.0d,
                    "total area must match exactly (tess=$tess)")
            assertEquals(v2.points.count, v3.points.count,
                    "point count must match (tess=$tess)")

            List<Atom> p2 = v2.points.list
            List<Atom> p3 = v3.points.list
            for (int i = 0; i < p2.size(); i++) {
                assertEquals(p2[i].x, p3[i].x, 0.0d, "x[$i] tess=$tess")
                assertEquals(p2[i].y, p3[i].y, 0.0d, "y[$i] tess=$tess")
                assertEquals(p2[i].z, p3[i].z, 0.0d, "z[$i] tess=$tess")
            }
        }
    }

    /**
     * The new default {@code packed_distinct_v4} (fused single-pass weighted scan) must be bit-for-bit
     * identical to {@code packed_distinct_v3} at the p2rank level: same total area, same point count, and
     * the same point coordinates in the same order. The fusion only changes HOW survivors are emitted, so
     * the surface (and hence every downstream p2rank feature) is unchanged.
     */
    @Test
    void packedDistinctV4MatchesV3Exactly() {
        IAtomContainer c = load()
        double sr = 1.6
        for (int tess in [2, 3, 4]) {
            SurfaceStrategy.RawSurface v3 = SurfaceStrategy.PACKED_DISTINCT_V3.compute(c, sr, tess)
            SurfaceStrategy.RawSurface v4 = SurfaceStrategy.PACKED_DISTINCT_V4.compute(c, sr, tess)

            assertEquals(v3.totalSurfaceArea, v4.totalSurfaceArea, 0.0d,
                    "total area must match exactly (tess=$tess)")
            assertEquals(v3.points.count, v4.points.count,
                    "point count must match (tess=$tess)")

            List<Atom> p3 = v3.points.list
            List<Atom> p4 = v4.points.list
            for (int i = 0; i < p3.size(); i++) {
                assertEquals(p3[i].x, p4[i].x, 0.0d, "x[$i] tess=$tess")
                assertEquals(p3[i].y, p4[i].y, 0.0d, "y[$i] tess=$tess")
                assertEquals(p3[i].z, p4[i].z, 0.0d, "z[$i] tess=$tess")
            }
        }
    }

    /** The shipped default surface_strategy must resolve to the bit-exact fused V4. */
    @Test
    void defaultStrategyIsPackedDistinctV4() {
        assertEquals("packed_distinct_v4", new Params().surface_strategy,
                "default surface_strategy param")
        Params p = Params.inst
        String saved = p.surface_strategy
        try {
            p.surface_strategy = "packed_distinct_v4"
            assertEquals(SurfaceStrategy.PACKED_DISTINCT_V4, SurfaceStrategy.resolve(p),
                    "packed_distinct_v4 must resolve to the V4 strategy")
        } finally {
            p.surface_strategy = saved
        }
    }

    /**
     * The production justification for defaulting to a distinct strategy (packed_distinct_v4) is that it
     * stands in for the historical FASTER surface followed by the 0.05 A sparsification step. This pins the
     * actual geometric relationship between the two, so a future surface-engine change that drifts away
     * from it (genuinely different point geometry, not just near-duplicate bookkeeping) fails loudly here.
     *
     * NOTE: the two are NOT bit-identical, despite the "exactly what sparsification removes" wording on
     * the distinct strategies. sparsify() greedily drops every point within 0.05 A of an already-kept
     * point, whereas a distinct engine only drops EXACTLY coincident points. So the distinct set is a
     * coordinate-superset of FASTER+sparsify: any surplus point is a near-duplicate (within 0.05 A) that
     * sparsify would have thinned. On 2src this is a handful of points out of ~6800. The invariants below
     * encode that (superset + surplus-only-near-duplicates + tiny surplus), which is the strongest claim
     * that is actually true -- exact equality is false and a test asserting it would be wrong.
     *
     * float_distinct is excluded: its single-precision occlusion verdict is documented as approximate
     * (boundary points may flip), a different and larger source of divergence.
     */
    @Test
    void distinctStrategiesAreNearDuplicateSupersetOfFasterPlusSparsify() {
        IAtomContainer c = load()
        double sr = 1.6
        List<SurfaceStrategy> exactDistinct = [
                SurfaceStrategy.FASTER_DISTINCT, SurfaceStrategy.PACKED_DISTINCT,
                SurfaceStrategy.PACKED_DISTINCT_V2, SurfaceStrategy.PACKED_DISTINCT_V3,
                SurfaceStrategy.PACKED_DISTINCT_V4]
        for (int tess in [2, 3, 4]) {
            SurfaceStrategy.RawSurface faster = SurfaceStrategy.FASTER.compute(c, sr, tess)
            Atoms reference = AtomDeduplicator.sparsify(faster.points, Surface.SPARSIFY_DIST)
            List<Atom> refPts = reference.list
            Set<List<Double>> refSet = coordSet(refPts)

            for (SurfaceStrategy s : exactDistinct) {
                SurfaceStrategy.RawSurface d = s.compute(c, sr, tess)
                List<Atom> dPts = d.points.list

                assertEquals(faster.totalSurfaceArea, d.totalSurfaceArea, 0.0d,
                        "${s.id} area must equal FASTER (tess=$tess)")

                // distinct keeps at least as many points as faster+sparsify (it thins less aggressively)
                assertTrue(d.points.count >= reference.count,
                        "${s.id} must keep >= FASTER+sparsify points (tess=$tess): ${d.points.count} vs ${reference.count}")

                // every faster+sparsify point survives in the distinct set (coordinate-superset)
                Set<List<Double>> dSet = coordSet(dPts)
                assertTrue(dSet.containsAll(refSet),
                        "${s.id} must be a coordinate-superset of FASTER+sparsify (tess=$tess)")

                // every surplus point is merely a near-duplicate sparsify would have removed
                int surplus = 0
                for (Atom a : dPts) {
                    if (refSet.contains([a.x, a.y, a.z] as List<Double>)) continue
                    surplus++
                    assertTrue(minDistTo(a, refPts) <= Surface.SPARSIFY_DIST + 1e-9d,
                            "${s.id} surplus point must be within SPARSIFY_DIST of a kept point (tess=$tess)")
                }
                // sanity: divergence is a near-duplicate handful, not a different surface (< 0.5%)
                assertTrue(surplus <= reference.count * 0.005d,
                        "${s.id} surplus must be tiny (tess=$tess): $surplus of ${reference.count}")
            }
        }
    }

    private static Set<List<Double>> coordSet(List<Atom> atoms) {
        Set<List<Double>> set = new HashSet<>()
        for (Atom a : atoms) set.add([a.x, a.y, a.z] as List<Double>)
        return set
    }

    private static double minDistTo(Atom a, List<Atom> pts) {
        double best = Double.MAX_VALUE
        for (Atom p : pts) {
            double dx = a.x - p.x, dy = a.y - p.y, dz = a.z - p.z
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz)
            if (d < best) best = d
        }
        return best
    }

    @Test
    void allStrategiesProduceAreaAndPoints() {
        IAtomContainer c = load()
        for (SurfaceStrategy s : SurfaceStrategy.values()) {
            SurfaceStrategy.RawSurface raw = s.compute(c, 1.6d, 3)
            assertTrue(raw.totalSurfaceArea > 0, "area > 0 for ${s.id}")
            assertTrue(raw.points.count > 0, "points > 0 for ${s.id}")
            // distinct strategies de-duplicate internally and need no external sparsification; the rest do
            boolean expectSparsification = !s.id.contains('distinct')
            assertEquals(expectSparsification, s.requiresSparsification,
                    "sparsification flag must match strategy semantics (${s.id})")
        }
    }

    @Test
    void resolveHonorsParamAndDeprecatedAlias() {
        Params p = Params.inst
        String savedStrat = p.surface_strategy
        boolean savedOpt = p.use_optimized_surface
        try {
            p.surface_strategy = "packed";  p.use_optimized_surface = true
            assertEquals(SurfaceStrategy.PACKED, SurfaceStrategy.resolve(p), "explicit strategy wins")

            p.surface_strategy = "";  p.use_optimized_surface = true
            assertEquals(SurfaceStrategy.FASTER, SurfaceStrategy.resolve(p), "empty + opt=true -> faster")

            p.surface_strategy = "";  p.use_optimized_surface = false
            assertEquals(SurfaceStrategy.CDK, SurfaceStrategy.resolve(p), "empty + opt=false -> cdk (legacy)")

            p.surface_strategy = "CDK"  // case-insensitive
            assertEquals(SurfaceStrategy.CDK, SurfaceStrategy.resolve(p), "case-insensitive id")

            p.surface_strategy = "bogus"
            assertThrows(Exception.class, { SurfaceStrategy.resolve(p) }, "unknown strategy throws")
        } finally {
            p.surface_strategy = savedStrat
            p.use_optimized_surface = savedOpt
        }
    }
}
