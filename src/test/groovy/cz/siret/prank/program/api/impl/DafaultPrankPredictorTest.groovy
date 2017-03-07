package cz.siret.prank.program.api.impl

import cz.siret.prank.domain.Prediction;
import cz.siret.prank.program.api.PrankFacade;
import cz.siret.prank.program.api.PrankPredictor
import cz.siret.prank.utils.PathUtils
import cz.siret.prank.utils.StrUtils;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths

import static cz.siret.prank.utils.PathUtils.path;
import static org.junit.Assert.*;

import cz.siret.prank.utils.futils

/**
 *
 */
class DafaultPrankPredictorTest {

    Path installDir = Paths.get("distro").toAbsolutePath()
    Path dataDir = path installDir, "test_data"
    Path outDir = path installDir, "test_output"

    Path testFile1 = path dataDir, "2W83.pdb"
    Path testFile2 = path dataDir, "1fbl.pdb.gz"

    List<Path> testFiles = [
            testFile1,
            testFile2,
            path(dataDir, "liganated", "1a82a.pdb"),
            path(dataDir, "liganated", "1aaxa.pdb"),
            path(dataDir, "liganated", "1nlua.pdb"),
            path(dataDir, "liganated", "1t7qa.pdb"),
            path(dataDir, "liganated", "2ck3b.pdb")
    ]


    PrankPredictor predictor = PrankFacade.createPredictor(installDir);

    @Test
    public void predict() throws Exception {

        testFiles.each { doTestPredict(it) }

        //TODO: more comprehensive tests and messages
    }

    private void doTestPredict(Path protFile) {
        Prediction prediction = predictor.predict( protFile )

        String fname = protFile.fileName.toString()
        
        assertTrue "[$fname] SAS points empty", prediction.labeledPoints.size() > 0
        assertTrue "[$fname] Predicted no pockets", prediction.pockets.size() > 0
    }

    @Test
    public void runPrediction() throws Exception {

        futils.delete(outDir.toString())

        Path testOutDir = path(outDir, "predict_2W83_test")
        predictor.runPrediction( testFile1, testOutDir )

        def outf = testOutDir.toString() + "/2W83.pdb_predictions.csv"

        assertTrue futils.exists(outf)
        assertTrue futils.size(outf) > 0
    }

}