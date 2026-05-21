package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class SwinSiteLoaderTest {

    static String testResourcesDir = 'src/test/resources/data/predictions/swinsite'
    static String distroDir = 'distro/test_data'

    static final double DELTA = 0.00001d

    Prediction loadPrediction(String predictionDir, String proteinFile) {
        Protein queryProtein = Protein.load(proteinFile)
        new SwinSiteLoader().loadPrediction(predictionDir, queryProtein)
    }

    void assertSwinSitePrediction(Prediction p, int expectedCount, double expectedTopScore) {
        assertEquals expectedCount, p.pocketCount

        // pockets sorted by score descending
        for (int i = 1; i < p.pockets.size(); i++) {
            assertTrue p.pockets[i - 1].score >= p.pockets[i].score,
                    "Pockets should be sorted by score descending"
        }

        // ranks are 1-based and sequential
        for (int i = 0; i < p.pockets.size(); i++) {
            assertEquals i + 1, p.pockets[i].rank
            assertEquals "pocket.${i + 1}".toString(), p.pockets[i].name
        }

        if (expectedCount > 0) {
            assertEquals expectedTopScore, p.pockets[0].score, DELTA
        }

        for (int i = 0; i < p.pockets.size(); i++) {
            def pocket = (SwinSiteLoader.SwinSitePocket) p.pockets[i]

            assertNotNull pocket.centroid, "pocket ${pocket.name} should have centroid"
            assertNotNull pocket.gridPoints, "pocket ${pocket.name} should have gridPoints"
            assertTrue pocket.gridPoints.count > 0, "pocket ${pocket.name} should have non-empty gridPoints"
            assertFalse pocket.surfaceAtoms.empty, "pocket ${pocket.name} should have surfaceAtoms"
            assertTrue pocket.stats.realVolumeApprox > 0, "pocket ${pocket.name} should have positive volume"
        }
    }

    @Test
    void testSwinSite_1tjw_A_distroData() {
        Prediction p = loadPrediction(
                "$distroDir/predictions/swinsite/1tjw_A",
                "$distroDir/clean/1tjw_A.pdb"
        )
        assertSwinSitePrediction(p, 6, 0.2778d)
    }

    @Test
    void testSwinSite_1tjw_A_testResources() {
        Prediction p = loadPrediction(
                "$testResourcesDir/1tjw_A",
                "$distroDir/clean/1tjw_A.pdb"
        )
        assertSwinSitePrediction(p, 6, 0.2778d)
    }

    // Single-pocket case
    @Test
    void testSwinSite_1a26A() {
        Prediction p = loadPrediction(
                "$distroDir/predictions/swinsite/1a26A",
                "$distroDir/clean/1a26A.pdb"
        )
        assertSwinSitePrediction(p, 1, 0.8070d)
    }

    @Test
    void testSwinSite_1a2kC() {
        Prediction p = loadPrediction(
                "$distroDir/predictions/swinsite/1a2kC",
                "$distroDir/clean/1a2kC.pdb"
        )
        assertSwinSitePrediction(p, 4, 0.5351d)
    }

    @Test
    void testSwinSite_1afkA() {
        Prediction p = loadPrediction(
                "$distroDir/predictions/swinsite/1afkA",
                "$distroDir/clean/1afkA.pdb"
        )
        assertSwinSitePrediction(p, 3, 0.6907d)
    }

    /**
     * 1atlA grid files appear in the directory ordered by N (0,1,2) but their
     * scores are NOT monotonic (0.7288, 0.0664, 0.3433). Exercises the
     * sort-desc-by-score reranking path.
     */
    @Test
    void testSwinSite_1atlA_rerank() {
        Prediction p = loadPrediction(
                "$distroDir/predictions/swinsite/1atlA",
                "$distroDir/clean/1atlA.pdb"
        )
        assertSwinSitePrediction(p, 3, 0.7288d)
        assertEquals 0.3433d, p.pockets[1].score, DELTA
        assertEquals 0.0664d, p.pockets[2].score, DELTA
    }

    @Test
    void testSwinSite_1atlA_testResources() {
        Prediction p = loadPrediction(
                "$testResourcesDir/1atlA",
                "$distroDir/clean/1atlA.pdb"
        )
        assertSwinSitePrediction(p, 3, 0.7288d)
    }

    @Test
    void testSwinSite_1bqoB() {
        Prediction p = loadPrediction(
                "$distroDir/predictions/swinsite/1bqoB",
                "$distroDir/clean/1bqoB.pdb"
        )
        assertSwinSitePrediction(p, 2, 0.6535d)
    }

    /**
     * PredictionLoader contract: prediction.protein must be the queryProtein
     * passed in. See ConcavityLoaderTest for the full rationale.
     */
    @Test
    void predictionIsBoundToQueryProtein() {
        Protein queryProtein = Protein.load("$distroDir/clean/1tjw_A.pdb")
        Prediction p = new SwinSiteLoader().loadPrediction(
                "$distroDir/predictions/swinsite/1tjw_A", queryProtein)

        assertSame(queryProtein, p.protein)
    }

    /**
     * surfaceAtoms must reference the SAME Atom instances as queryProtein's
     * exposedAtoms (identity, not just equality), so downstream set ops
     * (DSO/DSWO overlap, BindingSite intersection) hit. SwinSite uses the
     * same cutoutShell-from-exposedAtoms pattern as ConcavityLoader; this
     * is the bug class that once shipped there. sasPoints must also be
     * derived so the pocket isn't a half-built object.
     */
    @Test
    void surfaceAtomsBelongToQueryProtein() {
        Protein queryProtein = Protein.load("$distroDir/clean/1tjw_A.pdb")
        queryProtein.calcuateSurfaceAndExposedAtoms()
        Prediction p = new SwinSiteLoader().loadPrediction(
                "$distroDir/predictions/swinsite/1tjw_A", queryProtein)

        def pocket = p.pockets[0]
        assertFalse(pocket.surfaceAtoms.empty)
        def a = pocket.surfaceAtoms.list[0]
        assertSame(a, queryProtein.exposedAtoms.withIndex().getByID(a.PDBserial))
        assertNotNull(pocket.sasPoints)
        assertFalse(pocket.sasPoints.empty)
    }

    @Test
    void testEmptyDirectory() {
        // missing/empty dir should produce 0 pockets, not throw
        Path tmp = Files.createTempDirectory("swinsite-empty-")
        try {
            Protein queryProtein = Protein.load("$distroDir/clean/1tjw_A.pdb")
            Prediction p = new SwinSiteLoader().loadPrediction(tmp.toString(), queryProtein)
            assertEquals 0, p.pocketCount
        } finally {
            Files.delete(tmp)
        }
    }

}
