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
import cz.siret.prank.program.routines.predict.external.FpocketRunner
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
        String fpocketOutBaseDir = "$outdir/fpocket"
        String fpocketTmpDir = "$outdir/tmp_fpocket_runs"


        FeatureExtractor extractor = FeatureExtractor.createFactory()

        Dataset.Result result = dataset.processItems { Dataset.Item item ->

            String fpocketOutDir
            if (runFpocketAdHoc) {
                fpocketOutDir = adHocRunFpocketForItem(item, fpocketOutBaseDir, fpocketTmpDir)
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

        cleanAfterFpocket(fpocketOutBaseDir, fpocketTmpDir)

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

                // residues can't be always calculated when rescoring since we don't cover all the surface with SAS points
                //
                //if (params.label_residues && pair.prediction.residueLabelings != null) {
                //    String resf = "$predDir/${item.label}_residues.csv"
                //    writeFile(resf, pair.prediction.residueLabelings.toCSV())
                //}
            }

            if (produceVisualizations) {
                new PredictionVisualizer(outdir).generateVisualizations(item, rescorer, pair)
            }
        }
    }

    private void cleanAfterFpocket(String fpocketOutBaseDir, String fpocketTmpDir) {
        if (runFpocketAdHoc) {
            if (!params.fpocket_keep_output) {
                Futils.delete(fpocketOutBaseDir)
            }
            if (Futils.isDirEmpty(fpocketTmpDir)) {  // if not empty keep to debug failed runs
                try {
                    Futils.delete(fpocketTmpDir)
                } catch (Exception e) {
                    log.warn "Failed to delete tmp fpocket directory [$fpocketTmpDir]: ${e.message}"
                }
            }
        }
    }

    private String adHocRunFpocketForItem(Dataset.Item item, String fpocketOutBaseDir, String tmpDir) {
        log.info "Running Fpocket ad-hoc for item [${item.label}]"
        try {
            item.columnValues.put(Dataset.COLUMN_PREDICTION, '') // reset in case fpocket run fails

            String structFileName = Futils.shortName(item.proteinFile)
            String fpocketOutDir = "$fpocketOutBaseDir/${structFileName}_out"
            String fpocketPredFile = "$fpocketOutDir/${fpocketPredictionFileName(structFileName)}"

            FpocketRunner.runFpocket(item.proteinFile, fpocketOutDir, tmpDir)

            log.info "Fpocket run finished successfully for [$structFileName] - output in [$fpocketOutDir] (${Futils.shortName(fpocketPredFile)})"

            item.setPocketPredictionFile(fpocketPredFile)

            return fpocketOutDir

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
