package cz.siret.prank.program.api.impl

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.Prediction
import cz.siret.prank.program.Main
import cz.siret.prank.program.api.PrankPredictor
import cz.siret.prank.program.params.ConfigLoader
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.PredictRoutine
import groovy.transform.CompileStatic

import java.nio.file.Path
import java.util.function.Function

/**
 * Implementation of prediction API
 */
@CompileStatic
class DafaultPrankPredictor implements PrankPredictor {

    private Params params = Params.INSTANCE
    private Path installDir

    public DafaultPrankPredictor(Path installDir) {
        this.installDir = installDir
    }

    @Override
    public Params getParams() {
        return params;
    }

    @Override
    public void loadConfig(Path configFile) {
        ConfigLoader.overrideConfig(params, configFile.toFile())
    }

    /**
     * Run prediction on a single file in memory. No filesystem output is produced.
     *
     * @param proteinFile path to PDB file
     * @param conservationForChain maps chainId to conservation file. May be null.
     * @return prediction object containing structure, predicted pockets and labeled points
     */
    @Override
    public Prediction predict(Path proteinFile, Function<String, File> conservationForChain) {
        Dataset dataset = Dataset.createSingleFileDataset(proteinFile.toString(), conservationForChain)

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
     * @param conservationForChain maps chainId to conservation file. May be null.
     * @return prediction object containing structure, predicted pockets and labeled points
     */
    @Override
    public Prediction runPrediction(Path proteinFile, Function<String, File> conservationForChain, Path outDir) {
        Dataset dataset = Dataset.createSingleFileDataset(proteinFile.toString(), conservationForChain);

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

        PredictRoutine predictRoutine = new PredictRoutine(
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
        PredictRoutine predictRoutine = PredictRoutine.createForInternalUse(dataset, Main.findModel(installDir.toString(), params))
        return predictRoutine.execute()
    }

}
