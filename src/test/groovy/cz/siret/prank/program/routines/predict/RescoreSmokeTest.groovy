package cz.siret.prank.program.routines.predict

import cz.siret.prank.domain.Dataset
import cz.siret.prank.program.params.ConfigLoader
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.Futils
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static org.junit.jupiter.api.Assertions.*

/**
 * End-to-end smoke test for the rescore command.
 * Verifies the pipeline from dataset loading through pocket rescoring to output generation.
 */
@Isolated
@ResourceLock("Params")
class RescoreSmokeTest {

    static final String FPOCKET_DS = "distro/test_data/fpocket.ds"
    static final String OUT_DIR = "distro/test_output/rescore_smoke_test"

    @BeforeAll
    static void initAll() {
        Params.INSTANCE = new Params()
        Params.inst.installDir = "distro"
        ConfigLoader.overrideConfig(Params.inst, new File("distro/config/default_rescore.groovy"))
        Params.inst.visualizations = false
    }

    @AfterAll
    static void tearDownAll() {
        Params.INSTANCE = new Params()
        try { Futils.delete(OUT_DIR) } catch (Exception ignored) {}
    }

    @Test
    void rescoreFpocketProducesExpectedOutput() {
        Dataset dataset = Dataset.loadFromFile(FPOCKET_DS)
        assertNotNull(dataset)
        assertTrue(dataset.size > 0, "fpocket.ds must have items")

        String model = Params.inst.installDir + "/models/" + Params.inst.model
        Dataset.Result result = new RescorePocketsRoutine(dataset, model, OUT_DIR).execute()

        assertFalse(result.hasErrors(), "rescore should not produce errors")

        // verify output files exist for the first protein
        String rescoredFile = OUT_DIR + "/1a82a.pdb_rescored.csv"
        assertTrue(Futils.exists(rescoredFile), "_rescored.csv must exist: $rescoredFile")

        List<String> lines = new File(rescoredFile).readLines()
        assertTrue(lines.size() >= 2, "must have header + at least 1 pocket")

        // rescored CSV is tabulated (whitespace-padded), normalize to verify column names
        String normalizedHeader = lines[0].trim().replaceAll(/\s+/, ',')
        assertEquals("name,score,rank,old_rank,change,change_visual_aid", normalizedHeader,
                "rescored CSV header columns must match exactly")

        // predictions.csv should also be produced
        String predictionsFile = OUT_DIR + "/1a82a.pdb_predictions.csv"
        assertTrue(Futils.exists(predictionsFile), "_predictions.csv must also exist")
    }
}
