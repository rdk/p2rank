package cz.siret.prank.program.api;

import cz.siret.prank.domain.Prediction;
import cz.siret.prank.features.api.ProcessedItemContext;
import cz.siret.prank.program.params.Params;

import java.nio.file.Path;

/**
 * Facade for making predictions with P2RANK algorithm.
 */
public abstract class PrankPredictor {

    /**
     * Get config object used by this predictor.
     * (For now still uses global static config object :/)
     */
    public abstract Params getParams();

    /**
     * Override default configuration.
     * @param configFile path to groovy config file
     */
    public abstract void loadConfig(Path configFile);


    /**
     * Run prediction on a single file in memory. No filesystem output is produced.
     *
     * @param proteinFile path to PDB file
     * @param context allows to specify supplementary data (as in multi-column datasets). May be null
     * @return prediction object containing structure, predicted pockets and labeled points
     */
    public abstract Prediction predict(Path proteinFile, ProcessedItemContext context);

    /**
     * Run prediction on a single file and write results to the filesystem to outDir.
     *
     * (beware of getParams().visualizations setting)
     *
     * @param proteinFile path to PDB file
     * @param context allows to specify supplementary data (as in multi-column datasets). May be null
     * @return prediction object containing structure, predicted pockets and labeled points
     */
    public abstract Prediction runPrediction(Path proteinFile, Path outDir, ProcessedItemContext context);


    // convenience methods

    /**
     * Run prediction on a single file in memory. No filesystem output is produced.
     *
     * @param proteinFile path to PDB file
     * @return prediction object containing structure, predicted pockets and labeled points
     */
    public Prediction predict(Path proteinFile) {
        return predict(proteinFile, null);
    }

    /**
     * Run prediction on a single file and write results to the filesystem to outDir.
     *
     * (beware of getParams().visualizations setting)
     *
     * @param proteinFile path to PDB file
     * @return prediction object containing structure, predicted pockets and labeled points
     */
    public Prediction runPrediction(Path proteinFile, Path outDir) {
        return runPrediction(proteinFile, outDir, null);
    }

}
