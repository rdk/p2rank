package cz.siret.prank.program.routines.predict

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.domain.labeling.BinaryLabeling
import cz.siret.prank.domain.labeling.LigandBasedResidueLabeler
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.prediction.pockets.rescorers.ModelBasedRescorer
import cz.siret.prank.prediction.pockets.rescorers.PocketRescorer
import cz.siret.prank.prediction.pockets.results.PredictionSummary
import cz.siret.prank.prediction.transformation.ScoreTransformer
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.program.routines.results.PredictResults
import cz.siret.prank.program.visualization.PredictionVisualizer
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.domain.labeling.ResidueLabelings.trainResidueScoreTransformers
import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.writeFile

/**
 * Routine for making (and evaluating) predictions
 *
 * Backs prank commands 'predict' and 'eval-predict'
 */
@Slf4j
@CompileStatic
class PredictPocketsRoutine extends Routine {

    Dataset dataset
    String modelf

    boolean collectStats = false
    boolean produceVisualizations = params.visualizations
    boolean produceFilesystemOutput = true


    PredictPocketsRoutine(Dataset dataset, String modelf, String outdir) {
        super(outdir)
        this.dataset = dataset
        this.modelf = modelf
    }

    static PredictPocketsRoutine createForInternalUse(Dataset dataset, String modelf) {
        PredictPocketsRoutine routine = new PredictPocketsRoutine(dataset, modelf, null)
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

        Model model = Model.load(modelf)

        String visDir = "$outdir/visualizations"
        String predDir = "$outdir"
        if (produceVisualizations) {
            mkdirs(visDir)
        }
        if (collectStats) {
            // keep predictions in subfolder when running eval-predict command
            predDir = "$outdir/predictions"
            mkdirs(predDir)
        }

        PredictResults stats = new PredictResults()
        FeatureExtractor extractor = FeatureExtractor.createFactory()

        if (!collectStats) {
            LoaderParams.ignoreLigandsSwitch = true
        }

        Dataset.Result result = dataset.processItems { Dataset.Item item ->

            PredictionPair pair = item.predictionPair
            ModelBasedRescorer rescorer = new ModelBasedRescorer(model, extractor)
            if (collectStats) {
                rescorer.collectStatsForProtein(pair.protein)
            }
            rescorer.reorderPockets(pair.prediction, item.context) // in this context reorderPockets() makes predictions


            generatePredictionOutputFiles(pair, item, rescorer, predDir)


            if (collectStats) {  // do eval, expects dataset with liganated proteins
                // add observed binary labeling for residues (only in eval-predict)
                if (params.label_residues && pair.prediction.residueLabelings!=null) {
                    BinaryLabeling observed = new LigandBasedResidueLabeler().getBinaryLabeling(pair.protein)
                    pair.prediction.residueLabelings.observed = observed
                }
                stats.evaluation.addPrediction(pair, pair.prediction.pockets)
                synchronized (stats.classStats) {
                    stats.classStats.addAll(rescorer.stats)
                }
            }

            if (!dataset.cached) {
                item.cachedPair = null
            }
        }

        // stats and score transformer training
        if (collectStats && produceFilesystemOutput) {
            String modelLabel = model.classifier.class.simpleName + " ($modelf)"
            stats.logAndStore(outdir, modelLabel)
            stats.logMainResults(outdir, modelLabel)

            if (params.train_score_transformers != null) {
                trainPocketScoreTransformers(outdir, stats)
            }
            if (params.label_residues && params.train_score_transformers_for_residues) {
                trainResidueScoreTransformers(outdir, stats.evaluation)
            }
        }

        write "predicting pockets finished in $timer.formatted"
        if (produceFilesystemOutput) {
            write "results saved to directory [${Futils.absPath(outdir)}]"
        }

        return result
    }

    private generatePredictionOutputFiles(PredictionPair pair, Dataset.Item item, ModelBasedRescorer rescorer, String outdir) {
        if (produceFilesystemOutput) {
            boolean outputPredictionFiles = produceFilesystemOutput && !params.output_only_stats
            if (outputPredictionFiles) {
                PredictionSummary psum = new PredictionSummary(pair.prediction)
                writeFile"$outdir/${item.label}_predictions.csv", psum.toCSV()

                if (params.label_residues && pair.prediction.residueLabelings != null) {
                    writeFile "$outdir/${item.label}_residues.csv", pair.prediction.residueLabelings.toCSV()
                }
            }

            if (produceVisualizations) {
                new PredictionVisualizer(outdir).generateVisualizations(item, rescorer, pair)
            }
        }
    }

    static trainPocketScoreTransformers(String outdir, PredictResults stats) {
        String scoreDir = "$outdir/score"
        mkdirs(scoreDir)
        for (String name : Params.inst.train_score_transformers) {
            try {
                ScoreTransformer transformer = ScoreTransformer.create(name)
                transformer.trainForPockets(stats.evaluation)
                String fname = "$scoreDir/${name}.json"
                writeFile(fname, ScoreTransformer.saveToJson(transformer))
                log.info "Trained score transformer '$name' written to: $fname"
            } catch (Exception e) {
                log.error("Failed to train score transformer '$name'", e)
            }
        }
    }


}
