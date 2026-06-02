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
    void allStrategiesProduceAreaAndPoints() {
        IAtomContainer c = load()
        for (SurfaceStrategy s : SurfaceStrategy.values()) {
            SurfaceStrategy.RawSurface raw = s.compute(c, 1.6d, 3)
            assertTrue(raw.totalSurfaceArea > 0, "area > 0 for ${s.id}")
            assertTrue(raw.points.count > 0, "points > 0 for ${s.id}")
            assertTrue(s.requiresSparsification, "current strategies all need sparsification (${s.id})")
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
