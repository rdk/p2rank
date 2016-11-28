package cz.siret.prank.program.api.impl

import cz.siret.prank.domain.Prediction;
import cz.siret.prank.program.api.PrankFacade;
import cz.siret.prank.program.api.PrankPredictor;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

import cz.siret.prank.utils.futils

/**
 *
 */
class DafaultPrankPredictorTest {

    Path installDir = Paths.get("distro").toAbsolutePath()
    Path testFile = Paths.get(installDir.toString(), "test_data", "2W83.pdb")

    Path outDir = Paths.get(installDir.toString(), "test_output", "predict_2W83_test")

    PrankPredictor predictor = PrankFacade.createPredictor(installDir);


    @Test
    public void predict() throws Exception {

        Prediction prediction = predictor.predict( testFile )

        assertTrue prediction.labeledPoints.size() > 0
        assertTrue prediction.pockets.size() > 0

        //TODO: more comprehensive tests and messages
    }

    @Test
    public void runPrediction() throws Exception {

        futils.delete(outDir.toString())

        predictor.runPrediction( testFile, outDir  )


        def outf = installDir.toString() + "/test_output/predict_2W83_test/2W83.pdb_predictions.csv"

        assertTrue futils.exists(outf)
        assertTrue futils.size(outf) > 0
    }

}