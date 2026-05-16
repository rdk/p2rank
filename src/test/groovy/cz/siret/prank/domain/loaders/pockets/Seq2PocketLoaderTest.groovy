package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class Seq2PocketLoaderTest {

    static String testResourcesDir = 'src/test/resources/data/predictions/seq2pocket'
    static String distroDir = 'distro/test_data'

    static final double DELTA = 0.00001d

    Prediction loadPrediction(String predictionDir, String proteinFile) {
        Protein queryProtein = Protein.load(proteinFile)
        new Seq2PocketLoader().loadPrediction(predictionDir, queryProtein)
    }

    void assertSeq2PocketPrediction(Prediction p, int expectedCount, double expectedTopScore) {
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
            def pocket = p.pockets[i]
            assertNotNull pocket.centroid, "pocket ${pocket.name} should have centroid"
            assertFalse pocket.surfaceAtoms.empty, "pocket ${pocket.name} should have surfaceAtoms"
        }
    }

    @Test
    void testSeq2Pocket_1a26A() {
        Prediction p = loadPrediction(
                "$distroDir/predictions/seq2pocket/1a26A",
                "$distroDir/clean/1a26A.pdb"
        )
        assertSeq2PocketPrediction(p, 4, 0.9256007982336957d)
    }

    @Test
    void testSeq2Pocket_1a2kC() {
        Prediction p = loadPrediction(
                "$distroDir/predictions/seq2pocket/1a2kC",
                "$distroDir/clean/1a2kC.pdb"
        )
        assertSeq2PocketPrediction(p, 6, 0.9913651315789473d)
    }

    @Test
    void testSeq2Pocket_1afkA() {
        Prediction p = loadPrediction(
                "$distroDir/predictions/seq2pocket/1afkA",
                "$distroDir/clean/1afkA.pdb"
        )
        assertSeq2PocketPrediction(p, 3, 0.762952302631579d)
    }

    @Test
    void testSeq2Pocket_1atlA() {
        Prediction p = loadPrediction(
                "$distroDir/predictions/seq2pocket/1atlA",
                "$distroDir/clean/1atlA.pdb"
        )
        assertSeq2PocketPrediction(p, 2, 0.9132465563322368d)
    }

    @Test
    void testSeq2Pocket_1bqoB() {
        Prediction p = loadPrediction(
                "$distroDir/predictions/seq2pocket/1bqoB",
                "$distroDir/clean/1bqoB.pdb"
        )
        assertSeq2PocketPrediction(p, 3, 0.987035858497191d)
    }

    @Test
    void testSeq2Pocket_1a26A_testResources() {
        Prediction p = loadPrediction(
                "$testResourcesDir/1a26A",
                "$distroDir/clean/1a26A.pdb"
        )
        assertSeq2PocketPrediction(p, 4, 0.9256007982336957d)
    }

    /**
     * Synthetic fixture: same 4 pockets as 1a26A but rows reordered (3,1,4,2).
     * Exercises the sort-desc-by-score reranking path.
     */
    @Test
    void testSeq2Pocket_unsorted_rerank() {
        Prediction p = loadPrediction(
                "$testResourcesDir/1a26A_unsorted",
                "$distroDir/clean/1a26A.pdb"
        )
        assertSeq2PocketPrediction(p, 4, 0.9256007982336957d)
        assertEquals 0.8320621914333768d, p.pockets[1].score, DELTA
        assertEquals 0.5913609095982143d, p.pockets[2].score, DELTA
        assertEquals 0.44988368107722354d, p.pockets[3].score, DELTA
    }

    /**
     * Real Seq2Pocket emits a header-only file when the model predicts zero
     * pockets for a protein (~0.6% of inputs on coach420/holo4k/pdbbind2020).
     */
    @Test
    void testSeq2Pocket_headerOnly() {
        Prediction p = loadPrediction(
                "$testResourcesDir/1a26A_headeronly",
                "$distroDir/clean/1a26A.pdb"
        )
        assertEquals 0, p.pocketCount
    }

    /**
     * Synthetic fixture: pocket2's atom_ids reference non-existent serials
     * (999000001..3). The loader should skip the degenerate pocket and keep
     * pocket1 and pocket3, renumbering ranks to 1..2 after the sort.
     */
    @Test
    void testSeq2Pocket_skipsPocketWithAllSerialsUnresolved() {
        Prediction p = loadPrediction(
                "$testResourcesDir/1a26A_unresolved",
                "$distroDir/clean/1a26A.pdb"
        )
        assertEquals 2, p.pocketCount
        assertEquals 0.9256007982336957d, p.pockets[0].score, DELTA
        assertEquals 0.5913609095982143d, p.pockets[1].score, DELTA
        assertEquals 1, p.pockets[0].rank
        assertEquals 2, p.pockets[1].rank
    }

    @Test
    void testEmptyDirectory() {
        // dir with no *_predictions.txt file should produce 0 pockets, not throw
        Path tmp = Files.createTempDirectory("seq2pocket-empty-")
        try {
            Protein queryProtein = Protein.load("$distroDir/clean/1a26A.pdb")
            Prediction p = new Seq2PocketLoader().loadPrediction(tmp.toString(), queryProtein)
            assertEquals 0, p.pocketCount
        } finally {
            Files.delete(tmp)
        }
    }

    /**
     * PredictionLoader contract: prediction.protein must be the queryProtein
     * passed in. See ConcavityLoaderTest for the full rationale.
     */
    @Test
    void predictionIsBoundToQueryProtein() {
        Protein queryProtein = Protein.load("$distroDir/clean/1a26A.pdb")
        Prediction p = new Seq2PocketLoader().loadPrediction(
                "$distroDir/predictions/seq2pocket/1a26A", queryProtein)

        assertSame(queryProtein, p.protein)
    }

}
