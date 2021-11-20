package cz.siret.prank.program.api.impl

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.Prediction
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.program.Main
import cz.siret.prank.program.api.PrankPredictor
import cz.siret.prank.program.params.ConfigLoader
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.predict.PredictPocketsRoutine
import groovy.transform.CompileStatic

import java.nio.file.Path

/**
 * Implementation of prediction API
 */
@CompileStatic
class DafaultPrankPredictor extends PrankPredictor {

    private Params params = Params.INSTANCE
    private Path installDir

    DafaultPrankPredictor(Path installDir) {
        this.installDir = installDir
        Params.inst.installDir = installDir // TODO refactor
    }

    @Override
    Params getParams() {
        return params
    }

    @Override
    void loadConfig(Path configFile) {
        ConfigLoader.overrideConfig(params, configFile.toFile())
    }

    /**
     * Run prediction on a single file in memory. No filesystem output is produced.
     *
     * @param proteinFile path to PDB file
     * @param context allows to specify supplementary data (as in multi-column datasets)
     * @return prediction object containing structure, predicted pockets and labeled points
     */
    @Override
    Prediction predict(Path proteinFile, ProcessedItemContext context) {
        Dataset dataset = Dataset.createSingleFileDataset(proteinFile.toString(), context)

        dataset.cached = true
        params.fail_fast = true

        predict(dataset)

        return dataset.getItems().get(0).predictionPair.prediction
    }

    /**
     * Run prediction on a single file and write results to the filesystem to outDir.
     *
     * (beware of getParams().visualizations setting)
     *
     * @param proteinFile path to PDB file
     * @return prediction object containing structure, predicted pockets and labeled points
     */
    @Override
    Prediction runPrediction(Path proteinFile, Path outDir, ProcessedItemContext context) {
        Dataset dataset = Dataset.createSingleFileDataset(proteinFile.toString(), context)

        dataset.cached = true
        params.fail_fast = true

        runPrediction(dataset, outDir)

        return dataset.getItems().get(0).predictionPair.prediction // TODO refactor so it's not dependent on dataset caching
    }

    /**
     * Run predictions and write results to the filesystem to outDir.
     *
     * @param dataset
     * @param outDir
     * @return
     */
    protected Dataset.Result runPrediction(Dataset dataset, Path outDir) {

        PredictPocketsRoutine predictRoutine = new PredictPocketsRoutine(
                dataset,
                Main.findModel(installDir.toString(), params),
                outDir.toString())

        return predictRoutine.execute()

    }

    /**
     * Run predictions in memory. No filesystem output is produced.
     *
     * @param dataset
     * @return
     */
    protected Dataset.Result predict(Dataset dataset) {
        PredictPocketsRoutine predictRoutine = PredictPocketsRoutine.createForInternalUse(dataset, Main.findModel(installDir.toString(), params))
        return predictRoutine.execute()
    }

}
