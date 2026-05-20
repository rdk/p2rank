package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.*

class PocketeerLoaderTest {

    static String testResourcesDir = 'src/test/resources/data/predictions/pocketeer'
    static String cifProteinDir = 'src/test/resources/data/fpocket/fpocket-4-2/cif'
    static String distroDir = 'distro/test_data'

    static final double DELTA = 0.00001d

    Prediction loadPrediction(String pocketsJsonFile, String proteinFile) {
        Protein queryProtein = Protein.load(proteinFile)
        new PocketeerLoader().loadPrediction(pocketsJsonFile, queryProtein)
    }

    void assertPocketeerPrediction(Prediction p, int expectedCount, double expectedTopScore) {
        assertEquals expectedCount, p.pocketCount

        // pockets sorted by score descending
        for (int i = 1; i < p.pockets.size(); i++) {
            assertTrue p.pockets[i - 1].score >= p.pockets[i].score,
                    "Pockets should be sorted by score descending"
        }

        // ranks are 1-based and sequential
        for (int i = 0; i < p.pockets.size(); i++) {
            assertEquals i + 1, p.pockets[i].rank
        }

        assertEquals expectedTopScore, p.pockets[0].score, DELTA

        for (int i = 0; i < p.pockets.size(); i++) {
            def pocket = (PocketeerLoader.PocketeerPocket) p.pockets[i]

            assertFalse pocket.surfaceAtoms.empty, "pocket ${pocket.name} should have surfaceAtoms"
            assertNotNull pocket.centroid, "pocket ${pocket.name} should have centroid"
            assertTrue pocket.volume > 0, "pocket ${pocket.name} should have positive volume"
            assertEquals pocket.volume, pocket.stats.realVolumeApprox, DELTA

            assertEquals pocket.nSpheres, pocket.alphaSpheres.size()
            assertEquals pocket.nResidues, pocket.pocketeerResidues.size()
            assertFalse pocket.sphereCenters.empty

            // verify alpha spheres
            for (PocketeerLoader.AlphaSphere sphere : pocket.alphaSpheres) {
                assertNotNull sphere.center
                assertTrue sphere.radius > 0
                assertEquals 4, sphere.atomIndices.size()
            }

            // verify residues
            for (PocketeerLoader.PocketeerResidue residue : pocket.pocketeerResidues) {
                assertNotNull residue.chainId
                assertNotNull residue.resName
            }
        }
    }

    // --- CIF test data from src/test/resources ---

    @Test
    void testPocketeer_1fbl_cif() {
        Prediction p = loadPrediction(
                "$testResourcesDir/pocketeer_1fbl.cif/pockets.json",
                "$cifProteinDir/1fbl.cif"
        )
        assertPocketeerPrediction(p, 6, 7.5225d)
    }

    @Test
    void testPocketeer_2W83_cif() {
        Prediction p = loadPrediction(
                "$testResourcesDir/pocketeer_2W83.cif/pockets.json",
                "$cifProteinDir/2W83.cif"
        )
        assertPocketeerPrediction(p, 15, 6.0815d)
    }

    // --- PDB test data from distro/test_data ---

    @Test
    void testPocketeer_1a82a() {
        Prediction p = loadPrediction(
                "$distroDir/predictions/pocketeer/1a82a/pockets.json",
                "$distroDir/clean/1a82a.pdb"
        )
        assertPocketeerPrediction(p, 3, 8.82975d)
    }

    @Test
    void testPocketeer_1aaxa() {
        Prediction p = loadPrediction(
                "$distroDir/predictions/pocketeer/1aaxa/pockets.json",
                "$distroDir/clean/1aaxa.pdb"
        )
        assertPocketeerPrediction(p, 8, 6.3485d)
    }

    @Test
    void testPocketeer_1nlu() {
        Prediction p = loadPrediction(
                "$distroDir/predictions/pocketeer/1nlu/pockets.json",
                "$distroDir/clean/1nlu.pdb"
        )
        assertPocketeerPrediction(p, 4, 6.8685d)
    }

    @Test
    void testPocketeer_1t7qa() {
        Prediction p = loadPrediction(
                "$distroDir/predictions/pocketeer/1t7qa/pockets.json",
                "$distroDir/clean/1t7qa.pdb"
        )
        assertPocketeerPrediction(p, 2, 3.225d)
    }

    @Test
    void testPocketeer_2ck3b() {
        Prediction p = loadPrediction(
                "$distroDir/predictions/pocketeer/2ck3b/pockets.json",
                "$distroDir/clean/2ck3b.pdb"
        )
        assertPocketeerPrediction(p, 3, 5.0235d)
    }

    /**
     * PredictionLoader contract: prediction.protein must be the queryProtein
     * passed in. See ConcavityLoaderTest for the full rationale.
     */
    @Test
    void predictionIsBoundToQueryProtein() {
        Protein queryProtein = Protein.load("$cifProteinDir/1fbl.cif")
        Prediction p = new PocketeerLoader().loadPrediction(
                "$testResourcesDir/pocketeer_1fbl.cif/pockets.json", queryProtein)

        assertSame(queryProtein, p.protein)
    }

    /**
     * Empty pockets.json (top-level []) loads without throwing and produces a
     * Prediction with zero pockets.
     */
    @Test
    void emptyPocketsJsonProducesEmptyPrediction(@TempDir Path tmp) {
        Path emptyPocketsFile = tmp.resolve("pockets.json")
        Files.writeString(emptyPocketsFile, "[]")

        Protein queryProtein = Protein.load("$cifProteinDir/1fbl.cif")
        Prediction p = new PocketeerLoader().loadPrediction(
                emptyPocketsFile.toString(), queryProtein)

        assertEquals(0, p.pocketCount)
        assertSame(queryProtein, p.protein)
    }

}
