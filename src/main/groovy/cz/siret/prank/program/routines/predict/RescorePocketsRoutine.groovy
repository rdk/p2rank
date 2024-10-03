package cz.siret.prank.program.routines.predict

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.prediction.pockets.rescorers.ModelBasedRescorer
import cz.siret.prank.prediction.pockets.rescorers.PocketRescorer
import cz.siret.prank.prediction.pockets.results.RescoringSummary
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.program.routines.predict.external.FpocketRunner
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

    RescorePocketsRoutine(Dataset dataSet, String modelf, String outdir, boolean runFpocketAdHoc = false) {
        super(outdir)
        this.dataset = dataSet
        this.modelf = modelf
        this.runFpocketAdHoc = runFpocketAdHoc
    }

    private void checkDataset() {
        if (runFpocketAdHoc) {
            if (dataset.hasPredictionColumn()) {
                log.info "Dataset $dataset.name already contains prediction column; it will be ignored since ad-hoc fpocket prediction was requested."
            }
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
            dataset.attributes.put(Dataset.PARAM_PREDICTION_METHOD, 'fpocket')
        }


        FeatureExtractor extractor = FeatureExtractor.createFactory()

        Dataset.Result result = dataset.processItems { Dataset.Item item ->

            if (runFpocketAdHoc) {
                adHocRunFpocketForItem(item)
            }

            PredictionPair pair = item.predictionPair
            Prediction prediction = pair.prediction

            PocketRescorer rescorer = new  ModelBasedRescorer(model, extractor)
            rescorer.reorderPockets(prediction, item.context)

            RescoringSummary rsum = new RescoringSummary(prediction)
            writeFile "$outdir/${item.label}_rescored.csv", rsum.toCSV()
            log.info "\n\nRescored pockets for [$item.label]: \n\n" + rsum.toTable() + "\n"

        }

        write "rescoring finished in $timer.formatted"
        write "results saved to directory [${Futils.absPath(outdir)}]"

        return result
    }

    private adHocRunFpocketForItem(Dataset.Item item) {
        log.info "Running Fpocket ad-hoc for item [${item.label}]"
        try {
            item.columnValues.put(Dataset.COLUMN_PREDICTION, '') // reset in case fpocket run fails

            String structFileName = Futils.shortName(item.proteinFile)
            String fpocketOutDir = "$outdir/fpocket_${structFileName}_out"
            String tmpDir = "$outdir/tmp_fpocket_runs"
            String fpocketPredFile = "$fpocketOutDir/${fpocketPredictionFileName(structFileName)}"

            FpocketRunner.runFpocket(item.proteinFile, fpocketOutDir, tmpDir)

            log.info "Fpocket run finished successfully for [$structFileName] - output in [$fpocketOutDir] (${Futils.shortName(fpocketPredFile)})"

            item.setPocketPredictionFile(fpocketPredFile)

        } catch (Exception e) {
            throw new PrankException("Fpocket run failed for ${item.label}: ${e.message}", e)
        }
    }

    private static String fpocketPredictionFileName(String structFileName) {
        structFileName = Futils.shortName(structFileName)
        String structBaseName = Futils.baseName(structFileName)
        String structExtension = Futils.realExtension(structFileName) // aaaa.pdb.gz -> pdb
        return "${structBaseName}_out.${structExtension}"
    }

}
