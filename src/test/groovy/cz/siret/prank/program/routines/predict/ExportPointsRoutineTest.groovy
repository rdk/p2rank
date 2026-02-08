package cz.siret.prank.program.routines.predict

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import org.junit.jupiter.api.*
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static org.junit.jupiter.api.Assertions.*

/**
 * Integration tests for export-points command.
 * Uses existing test data — no additional downloads required.
 */
@Isolated
@ResourceLock("Params")
@CompileStatic
class ExportPointsRoutineTest {

    static final String TEST_DATA = "distro/test_data"
    static final String PDB_1FBL = "$TEST_DATA/1fbl.pdb"
    static final String OUT_DIR = "$TEST_DATA/../test_output/export_points_test"

    static Params originalParams
    static boolean origIgnoreLigandsSwitch

    @BeforeAll
    static void setup() {
        originalParams = (Params) Params.inst.clone()
        origIgnoreLigandsSwitch = LoaderParams.ignoreLigandsSwitch
        Params.INSTANCE = new Params()
    }

    @AfterAll
    static void tearDown() {
        Params.INSTANCE = originalParams
        LoaderParams.ignoreLigandsSwitch = origIgnoreLigandsSwitch
        try { Futils.delete(OUT_DIR) } catch (Exception ignored) {}
    }

    @BeforeEach
    void resetParams() {
        Params.INSTANCE = new Params()
    }

    @Test
    void exportsCsvWithDefaultFeatures() {
        Params.inst.export_points_format = "csv"

        Dataset dataset = Dataset.createSingleFileDataset(PDB_1FBL)
        String outdir = "$OUT_DIR/csv_default"

        Dataset.Result result = new ExportPointsRoutine(dataset, outdir).execute()

        assertFalse(result.hasErrors(), "Should not have errors")

        String csvFile = "$outdir/1fbl.pdb_points.csv"
        assertTrue(Futils.exists(csvFile), "CSV file should exist")
        assertTrue(Futils.size(csvFile) > 0, "CSV file should not be empty")

        // Verify header has no score column
        String firstLine = new File(csvFile).readLines().first()
        assertTrue(firstLine.startsWith("x,y,z,"), "Should start with x,y,z")
        assertFalse(firstLine.contains("score"), "Should not contain score column")

        // Verify has feature columns
        assertTrue(firstLine.contains("chem."), "Should contain chem features")

        // Verify has data rows (default tessellation=2 gives ~5000 points for ~200 residue protein)
        int lineCount = new File(csvFile).readLines().size()
        assertTrue(lineCount > 1000, "Should have >1000 data rows (SAS points), got $lineCount")
    }

    @Test
    void exportsParquet() {
        Params.inst.export_points_format = "parquet"

        Dataset dataset = Dataset.createSingleFileDataset(PDB_1FBL)
        String outdir = "$OUT_DIR/parquet"

        Dataset.Result result = new ExportPointsRoutine(dataset, outdir).execute()

        assertFalse(result.hasErrors())

        String pqFile = "$outdir/1fbl.pdb_points.parquet"
        assertTrue(Futils.exists(pqFile), "Parquet file should exist")
        assertTrue(Futils.size(pqFile) > 0, "Parquet file should not be empty")
    }

    @Test
    void writesParamsFile() {
        Params.inst.export_points_format = "csv"

        Dataset dataset = Dataset.createSingleFileDataset(PDB_1FBL)
        String outdir = "$OUT_DIR/params_check"

        new ExportPointsRoutine(dataset, outdir).execute()

        assertTrue(Futils.exists("$outdir/params.txt"), "params.txt should exist")
    }

    @Test
    void featureCountMatchesHeader() {
        Params.inst.export_points_format = "csv"

        Dataset dataset = Dataset.createSingleFileDataset(PDB_1FBL)
        String outdir = "$OUT_DIR/feature_count"

        new ExportPointsRoutine(dataset, outdir).execute()

        String csvFile = "$outdir/1fbl.pdb_points.csv"
        List<String> lines = new File(csvFile).readLines()
        String header = lines.first()
        String dataLine = lines.get(1)

        int headerCols = header.split(",").length
        int dataCols = dataLine.split(",").length
        assertEquals(headerCols, dataCols,
            "Header columns ($headerCols) should match data columns ($dataCols)")
    }

    @Test
    void noScoreColumnInOutput() {
        Params.inst.export_points_format = "csv"

        Dataset dataset = Dataset.createSingleFileDataset(PDB_1FBL)
        String outdir = "$OUT_DIR/no_score"

        new ExportPointsRoutine(dataset, outdir).execute()

        String csvFile = "$outdir/1fbl.pdb_points.csv"
        String header = new File(csvFile).readLines().first()
        List<String> columns = header.split(",").toList()

        // First 3 columns are coordinates
        assertEquals("x", columns[0])
        assertEquals("y", columns[1])
        assertEquals("z", columns[2])

        // 4th column should be a feature, not "score"
        assertNotEquals("score", columns[3], "4th column should be a feature, not score")
    }
}
