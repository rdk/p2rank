package cz.siret.prank.program.api;

import cz.siret.prank.program.api.impl.DafaultPrankPredictor;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Facade for using P2RANK as a library.
 *
 * For now, distribution of P2RANK must be located on the filesystem
 * and PrankPredictor must be initialized with a path pointing to the
 * directory where P2RANK is installed.
 *
 * Usage:
 * <pre>
 *      PrankPredictor prank = PrankFacade.createPredictor(Paths.get("/opt/p2rank"));
 *
 *      prank.loadConfig(Paths.get("/opt/p2rank/config/other-config.groovy")); // optional
 *      prank.getParams().setModel(...)                                        // optional
 *      prank.getParams().setXxxx(...)                                         // optional
 *
 *      Prediction pred = prank.predict(Paths.get("XXXX.pdb"));                // OR
 *      prank.runPrediction(Paths.get("XXXX.pdb"), Paths.get("/output/dir"));
 * </pre>
 */
public abstract class PrankFacade {

    /**
     * Create and initialize new PrankPredictor with default prediction config and model.
     *
     * @param prankInstallDir
     * @return
     */
    public static PrankPredictor createPredictor(Path prankInstallDir) {
        PrankPredictor predictor = new DafaultPrankPredictor(prankInstallDir);

        Path defaultConfigFile = Paths.get(prankInstallDir.toString(), "config", "default.groovy");
        predictor.loadConfig(defaultConfigFile);

        return predictor;
    }

}
