package cz.siret.prank.program.routines

import cz.siret.prank.domain.LoaderParams
import groovy.util.logging.Slf4j
import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.rendering.PyMolRenderer
import cz.siret.prank.score.PocketRescorer
import cz.siret.prank.score.WekaSumRescorer
import cz.siret.prank.score.results.PredictionSummary
import cz.siret.prank.utils.ATimer
import cz.siret.prank.utils.WekaUtils
import cz.siret.prank.utils.Writable
import cz.siret.prank.utils.futils
import weka.classifiers.Classifier

@Slf4j
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
        def timer = ATimer.start()

        write "predicting pockets for proteins from dataset [$dataset.name]"

        if (produceFilesystemOutput) {
            futils.mkdirs(outdir)
            writeParams(outdir)
            log.info "outdir: $outdir"
        }

        Classifier classifier = WekaUtils.loadClassifier(modelf)

        // try to make sure that classifer uses only one thread for each classification (we then parallelize dataset)
        String[] threadPropNames = ["numThreads","numExecutionSlots"]   // names used for num.threads property by different classifiers
        threadPropNames.each { name ->
            if (classifier.hasProperty(name))
                classifier."$name" = 1 // params.threads
        }

        String visDir = "$outdir/visualizations"
        if (produceVisualizations) {
            futils.mkdirs(visDir)
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
                    futils.overwrite(outf, psum.toCSV().toString())
                }

                if (collectStats) {  // expects dataset with liganated proteins
                    stats.predictionsEval.addPrediction(pair, pair.prediction.reorderedPockets)
                    synchronized (stats.classifierStats) {
                        stats.classifierStats.addAll(rescorer.stats)
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
            write "results saved to directory [${futils.absPath(outdir)}]"
        }


        return result
    }

}
