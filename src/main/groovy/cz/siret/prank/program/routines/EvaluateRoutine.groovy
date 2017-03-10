package cz.siret.prank.program.routines

import cz.siret.prank.score.PLBIndexRescorer
import cz.siret.prank.score.PocketRescorer
import cz.siret.prank.score.PocketVolumeRescorer
import cz.siret.prank.score.RandomRescorer
import cz.siret.prank.score.WekaSumRescorer
import groovy.util.logging.Slf4j
import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.program.rendering.PyMolRenderer
import cz.siret.prank.utils.ATimer
import cz.siret.prank.utils.WekaUtils
import cz.siret.prank.utils.futils
import weka.classifiers.Classifier

/**
 * Evaluate model on dataset
 */
@Slf4j
class EvaluateRoutine extends CompositeRoutine {

    Dataset dataset
    Classifier classifier
    String label
    Results results

    EvaluateRoutine(Dataset dataSet, String modelf, String outdir) {
        this.dataset = dataSet
        this.classifier = WekaUtils.loadClassifier(modelf)
        this.label = new File(modelf).name
        this.outdir = outdir
    }

    EvaluateRoutine(Dataset dataSet, Classifier classifier, String classifierLabel, String outdir) {
        this.dataset = dataSet
        this.classifier = classifier
        this.label = classifierLabel
        this.outdir = outdir
    }

    PocketRescorer createRescorer(PredictionPair pair, FeatureExtractor extractor) {
        PocketRescorer rescorer
        switch ( params.rescorer ) {
            case "WekaSumRescorer":
                rescorer = new  WekaSumRescorer(classifier, extractor)
                rescorer.collectStats(pair.liganatedProtein)
                break
            case "PLBIndexRescorer":
                rescorer = new PLBIndexRescorer()
                break
            case "PocketVolumeRescorer":
                rescorer = new PocketVolumeRescorer()
                break
            case "RandomRescorer":
                rescorer = new RandomRescorer()
                break
            default:
                throw new RuntimeException("Invalid rescorer [$params.rescorer]!")
        }
        return rescorer
    }

    Results execute() {
        def timer = ATimer.start()

        write "evaluating results on dataset [$dataset.name]"
        futils.mkdirs(outdir)
        futils.overwrite("$outdir/params.txt", params.toString())
        String visDir = "$outdir/visualizations"
        if (params.visualizations) {
            futils.mkdirs(visDir)
        }

        results = new Results(1)
        FeatureExtractor extractor = FeatureExtractor.createFactory()

        Dataset.Result datasetResult = dataset.processItems(params.parallel, new Dataset.Processor() {
            void processItem(Dataset.Item item) {
                PredictionPair pair = item.predictionPair

                PocketRescorer rescorer = createRescorer(pair, extractor)
                rescorer.reorderPockets(pair.prediction)

                if (params.visualizations) {
                    new PyMolRenderer(visDir).visualizeHistograms(item, (WekaSumRescorer)rescorer, pair)
                }

                results.originalEval.addPrediction(pair, pair.prediction.pockets         )
                results.rescoredEval.addPrediction(pair, pair.prediction.reorderedPockets)

                if (rescorer instanceof WekaSumRescorer) {
                    synchronized (results.classifierStats) {
                        results.classifierStats.addAll(rescorer.stats)
                    }
                }

            }
        });

        results.logAndStore(outdir, classifier.class.simpleName)
        logSummaryResults(dataset.label, label, results)

        write "processed $results.originalEval.ligandCount ligands in $dataset.size files"
        logTime "model evaluation finished in $timer.formatted"
        write "results saved to directory [${futils.absPath(outdir)}]"

        results.evalTime = timer.time
        results.datasetResult = datasetResult

        return results
    }

}
