package cz.siret.prank.domain

import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.program.api.PrankFacade
import cz.siret.prank.program.api.PrankPredictor
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import java.nio.file.Path
import java.nio.file.Paths

import static cz.siret.prank.utils.PathUtils.path
import static org.junit.jupiter.api.Assertions.*

/**
 * Integration tests for pocket output filtering through the full prediction pipeline.
 */
@Isolated
@ResourceLock("Params")
@CompileStatic
class PocketFilterIntegrationTest {

    static Path installDir = Paths.get("distro").toAbsolutePath()
    static Path dataDir = path(installDir, "test_data")
    static Path proteinFile = path(dataDir, "2W83.pdb")

    @BeforeAll
    static void init() {
        Params.INSTANCE = new Params()
    }

    @AfterEach
    void resetFilterParams() {
        Params.inst.pred_max_pockets = 0
        Params.inst.pred_min_pocket_score = Double.NaN
        Params.inst.pred_min_pocket_probability = Double.NaN
        Params.inst.pred_min_pockets = 0
    }

    @AfterAll
    static void restore() {
        Params.INSTANCE = new Params()
        LoaderParams.ignoreLigandsSwitch = false
    }

    private PrankPredictor createPredictor() {
        PrankFacade.createPredictor(installDir)
    }

    @Test
    void noFilteringByDefault() {
        Prediction pred = createPredictor().predict(proteinFile)

        int allCount = pred.pockets.size()
        int outputCount = pred.outputPockets.size()

        assertTrue(allCount > 0, "should predict at least one pocket")
        assertEquals(allCount, outputCount, "no filtering by default")
    }

    @Test
    void predMaxPocketsLimitsOutput() {
        Params.inst.pred_max_pockets = 2

        Prediction pred = createPredictor().predict(proteinFile)

        assertTrue(pred.pockets.size() > 2, "protein should have more than 2 unfiltered pockets")
        assertEquals(2, pred.outputPockets.size(), "output should be capped at 2")
    }

    @Test
    void filteredPocketsHaveConsecutiveRanks() {
        Params.inst.pred_max_pockets = 3

        Prediction pred = createPredictor().predict(proteinFile)

        for (int i = 0; i < pred.outputPockets.size(); i++) {
            Pocket p = pred.outputPockets[i]
            assertEquals(i + 1, p.rank, "rank should be consecutive")
            assertEquals(i + 1, p.newRank, "newRank should be consecutive")
            assertEquals("pocket" + (i + 1), p.name, "name should match rank")
        }
    }

    @Test
    void labeledPointsReflectFilteredPockets() {
        Params.inst.pred_max_pockets = 1

        Prediction pred = createPredictor().predict(proteinFile)

        Set<Integer> pocketValues = pred.labeledPoints
                .findAll { it.pocket > 0 }
                .collect { it.pocket }
                .toSet()

        assertEquals([1] as Set, pocketValues,
                "with max_pockets=1, only pocket 1 should appear in labeled points")
    }

    @Test
    void unfilteredPocketsPreservedForEval() {
        Params.inst.pred_max_pockets = 1

        Prediction pred = createPredictor().predict(proteinFile)

        assertTrue(pred.pockets.size() > 1,
                "unfiltered list should have all pockets")
        assertEquals(1, pred.outputPockets.size(),
                "output should have 1 pocket")
    }

    @Test
    void predMinScoreFiltersLowScoringPockets() {
        Prediction unfilteredPred = createPredictor().predict(proteinFile)
        double topScore = unfilteredPred.pockets[0].newScore

        Params.INSTANCE = new Params()
        Params.inst.pred_min_pocket_score = topScore * 0.9

        Prediction filteredPred = createPredictor().predict(proteinFile)

        assertTrue(filteredPred.outputPockets.size() < filteredPred.pockets.size(),
                "score filter should remove some pockets")
        for (Pocket p : filteredPred.outputPockets) {
            assertTrue(p.newScore >= topScore * 0.9,
                    "all output pockets should meet score threshold")
        }
    }
}
