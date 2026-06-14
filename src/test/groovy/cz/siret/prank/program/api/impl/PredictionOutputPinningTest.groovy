package cz.siret.prank.program.api.impl

import cz.siret.prank.program.api.PrankFacade
import cz.siret.prank.program.api.PrankPredictor
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import java.nio.file.Path
import java.nio.file.Paths

import static cz.siret.prank.utils.PathUtils.path
import static org.junit.jupiter.api.Assertions.*

/**
 * Pins the exact output format of _predictions.csv and _residues.csv.
 * Catches column renames, reordering, and formatting regressions.
 */
@Isolated
@ResourceLock("Params")
@CompileStatic
class PredictionOutputPinningTest {

    static Path installDir = Paths.get("distro").toAbsolutePath()
    static Path dataDir = path(installDir, "test_data")
    static Path outDir = path(installDir, "test_output", "pinning_test")

    static PrankPredictor predictor

    static Locale savedLocale

    @BeforeAll
    static void initAll() {
        // Run the whole prediction under a HOSTILE comma-decimal locale (cs_CZ) on purpose.
        // P2Rank's CSV/number output relies on the en_US default locale being pinned at the
        // entry point (Main.main / DafaultPrankPredictor). If that pin is ever lost, "%.4f"
        // emits "0,5000" and the residues CSV column count breaks. Forcing the hostile locale
        // here makes that regression fail on EVERY machine and in CI, not only on systems
        // whose default locale happens to use a comma separator.
        savedLocale = Locale.getDefault()
        Locale.setDefault(new Locale("cs", "CZ"))

        Params.INSTANCE = new Params()
        predictor = PrankFacade.createPredictor(installDir)
        Futils.delete(outDir.toString())
        predictor.runPrediction(path(dataDir, "2W83.pdb"), outDir)
    }

    @AfterAll
    static void tearDownAll() {
        if (savedLocale != null) Locale.setDefault(savedLocale)
        Params.INSTANCE = new Params()
        try { Futils.delete(outDir.toString()) } catch (Exception ignored) {}
    }

    @Test
    void predictionsCSVHeaderIsPinned() {
        String predictionsFile = outDir.toString() + "/2W83.pdb_predictions.csv"
        assertTrue(Futils.exists(predictionsFile), "predictions CSV must exist")

        List<String> lines = new File(predictionsFile).readLines()
        assertTrue(lines.size() >= 2, "must have header + at least 1 pocket")

        String expectedHeader = "name, rank, score, probability, sas_points, surf_atoms, center_x, center_y, center_z, residue_ids, surf_atom_ids"
        assertEquals(expectedHeader, lines[0].trim(), "predictions CSV header must match exactly")

        String[] cols = lines[1].split(", ", 11)
        assertEquals(11, cols.length, "data row must have 11 columns")

        assertTrue(cols[0].startsWith("pocket"), "name starts with 'pocket'")
        assertEquals("1", cols[1].trim(), "first row rank is 1")

        double score = Double.parseDouble(cols[2].trim())
        assertTrue(score > 0, "score > 0")

        double probability = Double.parseDouble(cols[3].trim())
        assertTrue(probability >= 0 && probability <= 1, "probability in [0,1]: $probability")

        double cx = Double.parseDouble(cols[6].trim())
        double cy = Double.parseDouble(cols[7].trim())
        double cz = Double.parseDouble(cols[8].trim())
        assertTrue(Double.isFinite(cx) && Double.isFinite(cy) && Double.isFinite(cz), "coordinates are finite")
    }

    @Test
    void residuesCSVHeaderIsPinned() {
        String residuesFile = outDir.toString() + "/2W83.pdb_residues.csv"
        assertTrue(Futils.exists(residuesFile), "residues CSV must exist")

        List<String> lines = new File(residuesFile).readLines()
        assertTrue(lines.size() >= 2, "must have header + at least 1 residue")

        String expectedHeader = "chain, residue_label, residue_name, score, zscore, probability, pocket"
        assertEquals(expectedHeader, lines[0].trim(), "residues CSV header must match exactly")

        int headerColCount = lines[0].trim().split(",").length
        assertEquals(7, headerColCount, "residues CSV must have exactly 7 columns")

        for (int i = 1; i < Math.min(lines.size(), 5); i++) {
            int dataColCount = lines[i].split(",").length
            assertEquals(headerColCount, dataColCount,
                    "row $i column count must match header ($headerColCount)")
        }
    }

    @Test
    void predictionsCSVScoresAreReasonable() {
        String predictionsFile = outDir.toString() + "/2W83.pdb_predictions.csv"
        List<String> lines = new File(predictionsFile).readLines()

        // pockets must be sorted by score descending (ties allowed)
        double prevScore = Double.MAX_VALUE
        for (int i = 1; i < lines.size(); i++) {
            String[] cols = lines[i].split(", ", 11)
            double score = Double.parseDouble(cols[2].trim())
            assertTrue(score <= prevScore,
                    "pockets must be sorted by score descending: row $i score=$score > prev=$prevScore")
            prevScore = score
        }
    }
}
