package cz.siret.prank.program.routines.traineval

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.prediction.pockets.rescorers.*
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.rendering.OldPymolRenderer
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Futils.mkdirs

/**
 * Evaluate a model on a dataset.
 * Pocket centric (for pocket prediction and rescoring).
 */
@Slf4j
@CompileStatic
class EvalPocketsRoutine extends EvalRoutine {

    Dataset dataset
    Model model
    private EvalResults results

    EvalPocketsRoutine(Dataset dataSet, Model model, String outdir) {
        super(outdir)
        this.dataset = dataSet
        this.model = model
    }

    EvalResults getResults() {
        return results
    }

    private PocketRescorer createRescorer(PredictionPair pair, FeatureExtractor extractor) {
        PocketRescorer rescorer
        switch ( params.rescorer ) {
            case "ModelBasedRescorer":
                rescorer = new ModelBasedRescorer(model, extractor)
                rescorer.collectStatsForProtein(pair.protein)
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
            case "IdentityRescorer":
                rescorer = new IdentityRescorer()
                break
            default:
                throw new RuntimeException("Invalid rescorer [$params.rescorer]!")
        }
        return rescorer
    }

    @Override
    EvalResults execute() {
        def timer = startTimer()

        write "evaluating results on dataset [$dataset.name]"
        mkdirs(outdir)
        writeParams(outdir)

        String visDir = "$outdir/visualizations"
        if (params.visualizations) {
            mkdirs(visDir)
        }

        String orig_pockets_dir = "$outdir/original_pockets"
        if (!params.predictions) {
            mkdirs(orig_pockets_dir)
        }

        results = new EvalResults(1)
        final FeatureExtractor extractor = FeatureExtractor.createFactory()

        results.datasetResult = dataset.processItems { Dataset.Item item ->

            PredictionPair pair = item.predictionPair
            PocketRescorer rescorer = createRescorer(pair, extractor)
            rescorer.reorderPockets(pair.prediction, item.context)

            if (params.visualizations) {
                new OldPymolRenderer(visDir).render(item, (ModelBasedRescorer)rescorer, pair)
            }

            if (params.predictions) {
                results.eval.addPrediction(pair, pair.prediction.pockets)
            } else { // rescore
                results.eval.addPrediction(pair, pair.prediction.reorderedPockets)
                results.origEval.addPrediction(pair, pair.prediction.pockets)
                writeOriginalPocketStats(pair, orig_pockets_dir)
            }

            if (rescorer instanceof ModelBasedRescorer) {
                synchronized (results.classifierStats) {
                    results.classifierStats.addAll(rescorer.stats)
                }
            }

            if (!dataset.cached) {
                item.cachedPair = null
            }
        }

        results.logAndStore(outdir, model.classifier.class.simpleName)
        logSummaryResults(dataset.label, model.label, results)

        write "processed $results.origEval.ligandCount ligands in $dataset.size files"
        logTime "model evaluation finished in $timer.formatted"
        write "results saved to directory [${Futils.absPath(outdir)}]"

        results.firstEvalTime = timer.time

        return results
    }

    private writeOriginalPocketStats(PredictionPair pair, String dir) {
        String originalPocketsStr = pair.prediction.pockets.collect { Pocket p ->
            "$p.rank  $p.score  $p.name  $p.centroid.x  $p.centroid.y  $p.centroid.z".replace("  ", "\t")
        }.join("\n")
        Futils.writeFile "$dir/${pair.name}_pockets.txt", originalPocketsStr
    }

}
