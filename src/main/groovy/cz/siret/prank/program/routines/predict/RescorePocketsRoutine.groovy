package cz.siret.prank.program.routines.predict

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.prediction.pockets.rescorers.ModelBasedRescorer
import cz.siret.prank.prediction.pockets.rescorers.PocketRescorer
import cz.siret.prank.prediction.pockets.results.PredictionSummary
import cz.siret.prank.prediction.pockets.results.RescoringSummary
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.program.routines.predict.external.FpocketAdHocHelper
import cz.siret.prank.program.routines.predict.output.PocketGridOutputs
import cz.siret.prank.program.routines.predict.output.PointsExporter
import cz.siret.prank.program.visualization.PredictionVisualizer
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.writeFile

/**
 * EvalRoutine for rescoring pockets found by other methods (Fpocket, ConCavity) ... PRANK.
 */
@Slf4j
@CompileStatic
class RescorePocketsRoutine extends Routine {

    Dataset dataset
    String modelf
    boolean runFpocketAdHoc

    boolean produceVisualizations = params.visualizations
    boolean produceFilesystemOutput = true


    RescorePocketsRoutine(Dataset dataSet, String modelf, String outdir, boolean runFpocketAdHoc = false) {
        super(outdir)
        this.dataset = dataSet
        this.modelf = modelf
        this.runFpocketAdHoc = runFpocketAdHoc
    }

    private void checkDataset() {
        if (runFpocketAdHoc) {
            // prediction column not required — fpocket will generate predictions
        } else {
            if (!(dataset.hasProteinColumn() && dataset.hasPredictionColumn())) {
                throw new PrankException("Dataset must contain '${Dataset.COLUMN_PROTEIN}' and '${Dataset.COLUMN_PREDICTION}' columns!")
            }
        }
    }

    Dataset.Result execute() {
        def timer = startTimer()

        log.info "outdir: $outdir"
        mkdirs(outdir)
        writeParams(outdir)

        write "rescoring pockets on proteins from dataset [$dataset.name]"

        checkDataset()

        Model model = Model.load(modelf)

        if (params.rf_flatten && !params.delete_models) {
            model.saveToFile("$outdir/${model.label}_flattened.model")
        }

        if (runFpocketAdHoc) {
            FpocketAdHocHelper.prepareDataset(dataset)
        }
        String fpocketOutBaseDir = "$outdir/fpocket"
        String fpocketTmpDir = "$outdir/tmp_fpocket_runs"


        FeatureExtractor extractor = FeatureExtractor.createFactory()

        Dataset.Result result = dataset.processItems { Dataset.Item item ->

            String fpocketOutDir
            if (runFpocketAdHoc) {
                fpocketOutDir = FpocketAdHocHelper.runForItem(item, fpocketOutBaseDir, fpocketTmpDir)
            }

            PredictionPair pair = item.predictionPair
            Prediction prediction = pair.prediction

            PocketRescorer rescorer = new  ModelBasedRescorer(model, extractor)
            rescorer.reorderPockets(prediction, item.context)

            RescoringSummary rsum = new RescoringSummary(pair.prediction)


            generatePredictionOutputFiles(rsum, pair, item, rescorer, outdir)


            if (runFpocketAdHoc && !params.fpocket_keep_output) {
                Futils.delete(fpocketOutDir)
            }

            log.info "\n\nRescored pockets for [$item.label]: \n\n" + rsum.toTable() + "\n"
        }

        if (runFpocketAdHoc) {
            FpocketAdHocHelper.cleanup(fpocketOutBaseDir, fpocketTmpDir, params.fpocket_keep_output)
        }

        write "rescoring finished in $timer.formatted"
        write "results saved to directory [${Futils.absPath(outdir)}]"

        return result
    }

    private generatePredictionOutputFiles(RescoringSummary rsum, PredictionPair pair, Dataset.Item item, ModelBasedRescorer rescorer, String outdir) {
        if (produceFilesystemOutput) {
            boolean outputPredictionFiles = produceFilesystemOutput && !params.output_only_stats
            if (outputPredictionFiles) {
                writeFile "$outdir/${item.label}_rescored.csv", rsum.toCSV()

                PredictionSummary psum = new PredictionSummary(pair.prediction)
                writeFile "$outdir/${item.label}_predictions.csv", psum.toCSV()

                // Export SAS points with feature vectors and scores (pocket points only in rescore mode)
                PointsExporter.tryExportPoints(rescorer.exportData, outdir, item.label)

                // Pocket grid + descriptors export + optional PyMOL viz.
                PocketGridOutputs.exportIfEnabled(pair.prediction, item.protein, outdir, item.label)
            }

            if (produceVisualizations) {
                new PredictionVisualizer(outdir).generateVisualizations(item, rescorer, pair)
            }
        }
    }

}
