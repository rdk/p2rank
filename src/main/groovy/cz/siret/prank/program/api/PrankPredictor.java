package cz.siret.prank.program.api;

import cz.siret.prank.domain.Prediction;
import cz.siret.prank.program.params.Params;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Facade for making predictions with P2RANK algorithm.
 */
public interface PrankPredictor {

    /**
     * Get config object used by this predictor.
     * (For now still uses global static config object :/)
     * @return
     */
    Params getParams();

    /**
     * Override default configuration.
     * @param configFile
     */
    void loadConfig(Path configFile);

    /**
     * Run prediction on a single file in memory. No filesystem output is produced.
     *
     * @param proteinFile path to PDB file
     * @param conservationForChain maps chainId to conservation file. May be null.
     * @return prediction object containing structure, predicted pockets and labeled points
     */
    Prediction predict(Path proteinFile, Function<String, File> conservationForChain);

    /**
     * Run prediction on a single file and write results to the filesystem to outDir.
     *
     * (beware of getParams().visualizations setting)
     *
     * @param proteinFile path to PDB file
     * @param conservationForChain maps chainId to conservation file. May be null.
     * @return prediction object containing structure, predicted pockets and labeled points
     */
    Prediction runPrediction(Path proteinFile, Function<String, File> conservationForChain, Path outDir);

}
