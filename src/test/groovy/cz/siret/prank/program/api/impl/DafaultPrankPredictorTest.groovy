package cz.siret.prank.program.api.impl;

import cz.siret.prank.program.api.PrankFacade;
import cz.siret.prank.program.api.PrankPredictor;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

/**
 *
 */
public class DafaultPrankPredictorTest {



    Path getInstallDir() {
        Paths.get("src","distro")
    }

    Path getTestProtFile() {
        Paths.get(installDir.toString(), "test_data", "2W83.pdb")
    }



//    @Test
//    public void predict() throws Exception {
//        PrankPredictor predictor = PrankFacade.createPredictor(installDir);
//
//        String ptotf = installDir.toString() + "/"
//
//        predictor.predict( testProtFile )
//
//    }

    @Test
    public void runPrediction() throws Exception {
        PrankPredictor predictor = PrankFacade.createPredictor(installDir);

        predictor.runPrediction( testProtFile, Paths.get(installDir.toString(), "test_output", "predict_2W83_test")  )

    }

}