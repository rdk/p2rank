package cz.siret.prank.program.routines

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.LoaderParams
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.program.rendering.PyMolRenderer
import cz.siret.prank.program.routines.results.PredictResults
import cz.siret.prank.score.PocketRescorer
import cz.siret.prank.score.WekaSumRescorer
import cz.siret.prank.score.results.PredictionSummary
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.WekaUtils
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import weka.classifiers.Classifier

import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.writeFile


/**
 * Routine for making (and evaluating) predictions
 *
 * Backs prank commands 'predict' and 'eval-predict'
 *
 *
 */
@Slf4j
@CompileStatic
class PredictRoutine extends Routine {

    Dataset dataset
    String modelf

    boolean collectStats = false
    boolean produceVisualizations = params.visualizations
    boolean produceFilesystemOutput = true

    PredictRoutine(Dataset dataset, String modelf, String outdir) {
        this.dataset = dataset
        this.modelf = modelf
        this.outdir = outdir
    }

    static PredictRoutine createForInternalUse(Dataset dataset, String modelf) {
        PredictRoutine routine = new PredictRoutine(dataset, modelf, null)
        routine.produceFilesystemOutput = false
        routine.produceVisualizations = false
        return routine
    }



    Dataset.Result execute() {
        def timer = startTimer()

        write "predicting pockets for proteins from dataset [$dataset.name]"

        if (produceFilesystemOutput) {
            mkdirs(outdir)
            writeParams(outdir)
            log.info "outdir: $outdir"
        }

        Classifier classifier = WekaUtils.loadClassifier(modelf)
        WekaUtils.disableParallelism(classifier)

        String visDir = "$outdir/visualizations"
        if (produceVisualizations) {
            mkdirs(visDir)
        }

        PredictResults stats = new PredictResults()
        FeatureExtractor extractor = FeatureExtractor.createFactory()

        if (!collectStats) {
            LoaderParams.ignoreLigandsSwitch = true
        }

        boolean outputPredictionFiles = produceFilesystemOutput && !params.output_only_stats

        Dataset.Result result = dataset.processItems(params.parallel, new Dataset.Processor() {
            void processItem(Dataset.Item item) {

                PredictionPair pair = item.predictionPair
                PocketRescorer rescorer = new WekaSumRescorer(classifier, extractor)
                rescorer.reorderPockets(pair.prediction) // in this context reorderPockets() makes predictions

                if (produceVisualizations) {
                    new PyMolRenderer(visDir).visualizeHistograms(item, rescorer, pair)
                }

                if (outputPredictionFiles) {
                    PredictionSummary psum = new PredictionSummary(pair.prediction)
                    String outf = "$outdir/${item.label}_predictions.csv"
                    writeFile(outf, psum.toCSV().toString())
                }

                if (collectStats) {  // expects dataset with liganated proteins
                    stats.evaluation.addPrediction(pair, pair.prediction.reorderedPockets)
                    synchronized (stats.classStats) {
                        stats.classStats.addAll(rescorer.stats)
                    }
                }

                if (!dataset.cached) {
                    item.cachedPair = null
                }
            }
        })

        if (collectStats && produceFilesystemOutput) {
            String modelLabel = classifier.class.simpleName + " ($modelf)"
            stats.logAndStore(outdir, modelLabel)
            stats.logMainResults(outdir, modelLabel)
        }

        write "predicting pockets finished in $timer.formatted"

        if (produceFilesystemOutput) {
            write "results saved to directory [${Futils.absPath(outdir)}]"
        }


        return result
    }

}
