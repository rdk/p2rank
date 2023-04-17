package cz.siret.prank.program.routines.traineval

import cz.siret.prank.domain.Dataset
import cz.siret.prank.fforest.api.FlattableForest
import cz.siret.prank.prediction.metrics.ClassifierStats
import cz.siret.prank.program.ml.FeatureVectors
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.program.routines.results.FeatureImportances
import cz.siret.prank.utils.ATimer
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.WekaUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import weka.classifiers.Classifier
import weka.classifiers.trees.RandomForest
import weka.core.Instance

import javax.annotation.Nonnull
import javax.annotation.Nullable

import static cz.siret.prank.prediction.pockets.PointScoreCalculator.applyPointScoreThreshold
import static cz.siret.prank.prediction.pockets.PointScoreCalculator.normalizedScore
import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Formatter.formatTime
import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.writeFile

@Slf4j
@CompileStatic
class TrainEvalRoutine extends EvalRoutine implements Parametrized  {

    Dataset trainDataSet
    Dataset evalDataSet

    FeatureVectors trainVectors

    // may be same between iterations
    private String trainVectorFile
    private String evalVectorFile

    EvalRoutine evalRoutine

    boolean cacheModels = false
    @Nullable
    ModelCache modelCache

    TrainEvalRoutine(String outdir, Dataset trainData, Dataset evalData) {
        super(outdir)
        this.trainDataSet = Objects.requireNonNull(trainData, "Training dataset was not provided. Run with '-t {train_dataset}.ds'")
        this.evalDataSet = Objects.requireNonNull(evalData, "Evaluation dataset was not provided. Run with '-e {eval_dataset}.ds'")
    }

    TrainEvalRoutine withModelCache(@Nonnull ModelCache modelCache) {
        this.modelCache = modelCache
        this.cacheModels = true
        return this
    }

    EvalResults execute() {

        collectTrainVectors()
        EvalResults res = trainAndEvalModel()

        if (params.delete_vectors)
            deleteVectorFiles()

        return res
    }

    void collectTrainVectors() {
        if (!shouldTrainModel()) return
        
        String vectf =  "$outdir/vectorsTrain.arff"
        trainVectorFile = vectf
        trainVectors = doCollectVectors(trainDataSet, vectf)
    }

    /**
     * doesn't store any data, just writes them to file for further analysis
     */
    void collectEvalVectors() {
        String vectf =  "$outdir/vectorsEval.arff"
        evalVectorFile = vectf
        doCollectVectors(evalDataSet, vectf)
    }

    private FeatureVectors doCollectVectors(Dataset dataSet, String vectFileName) {
        ATimer timer = startTimer()

        mkdirs(outdir)

        dataSet.forTraining(true)
        CollectVectorsRoutine collector = new CollectVectorsRoutine(dataSet, outdir, vectFileName)

        FeatureVectors res = collector.collectVectors()

        logTime "vectors collected in " + timer.formatted

        return res
    }

    void deleteVectorFiles() {
        Futils.delete(trainVectorFile)
        Futils.delete(evalVectorFile)
    }

    ClassifierStats calculateTrainStats(Classifier classifier, FeatureVectors trainVectors) {
        if (params.classifier_train_stats) {
            ClassifierStats trainStats = new ClassifierStats()
            for (Instance inst : trainVectors.instances) {
                double[] hist = classifier.distributionForInstance(inst)
                double score = normalizedScore(hist)
                boolean predicted = applyPointScoreThreshold(score)
                boolean observed = inst.classValue() > 0

                trainStats.addPrediction(observed, predicted, score)
            }
            return trainStats
        } else {
            return null
        }
    }

    private String getModelCacheKey() {
        return params.seed.toString()
    }

    private boolean shouldTrainModel() {
        if (cacheModels) {
            return !modelCache.contains(modelCacheKey)
        } else {
            true
        }
    }

    EvalResults trainAndEvalModel() {
        def timer = startTimer()

        mkdirs(outdir)

        // TODO perform subsampling or supersampling if needed
        // -> move subsampling/supersampling logic from cz.siret.prank.collectors.DataPreprocessor.preProcessTrainData
        // here so before every seedloop run there is new random sampling

        long trainTime = 0
        ClassifierStats trainStats = null
        List<Double> featureImportances = null
        String modelf = null
        Model model = null

        if (shouldTrainModel()) {
            model = Model.createNewFromParams(params)

            write "training classifier ${model.classifier.getClass().name} on dataset with ${trainVectors.count} instances"

            def trainTimer = startTimer()
            trainModel(model, trainVectors)
            trainTime = trainTimer.time
            logTime "model trained in " + formatTime(trainTime)

            modelf = "$outdir/${model.label}.model"
            if (!params.delete_models) {
                model.saveToFile(modelf)
            }
            trainStats = calculateTrainStats(model.classifier, trainVectors)
            featureImportances = calcFeatureImportances(model)

            if (cacheModels) {
                write "storing model to cache (key: $modelCacheKey)"
                modelCache.put(modelCacheKey, model)
            }
        } else {
            write "loading model from cache (key: $modelCacheKey)"
            model = modelCache.get(modelCacheKey)
        }

        logTime "model prepared in " + timer.formatted
        timer.restart()


        evalRoutine = EvalRoutine.create(params.predict_residues, evalDataSet, model, outdir)

        EvalResults res = evalRoutine.execute()
        res.totalTrainingTime = trainTime
        res.train_positives = trainVectors.positives
        res.train_negatives = trainVectors.negatives
        res.featureImportances = featureImportances
        res.classifierTrainStats = trainStats

        logTime "evaluation routine on dataset [$evalDataSet.name] finished in " + timer.formatted

        if (params.delete_models)
            Futils.delete(modelf)

        return res
    }


    void trainModel(Model model, FeatureVectors data) {
        WekaUtils.trainClassifier(model.classifier, data)

        if (params.rf_flatten) {
            if (model.classifier instanceof FlattableForest) {
                log.info "Flattening random forest"
                def timer = startTimer()
                model.classifier = ((FlattableForest)model.classifier).toFlatBinaryForest()
                logTime "model flattened in " + timer.formatted

                model.label = model.label + "_flat"
            } else {
                log.warn("Trying to flatten classifier that does not support it: " + model.classifier.class.simpleName)
            }
        }
    }

    private List<Double> calcFeatureImportances(Model model) {
        List<Double> featureImportances = null

        if (params.feature_importances) {
            if (model.hasFeatureImportances()) {
                featureImportances = model.getFeatureImportances()
                if (featureImportances != null) {
                    def namedImportances = FeatureImportances.from(featureImportances)
                    def rowCsv =  namedImportances.names.join(',') + '\n' + namedImportances.values.collect {FeatureImportances.fmt_fi(it) }.join(',') + '\n'

                    def fname = "$outdir/feature_importances.csv"
                    write "Saving feature importances to file [$fname]"
                    writeFile fname, rowCsv
                    writeFile"$outdir/feature_importances_sorted.csv", namedImportances.sorted().toCsv()
                }
            } else if (model.classifier instanceof RandomForest) {
                // spacial case: weka RandomForest provides importances only in toString()
                def fname = "$outdir/feature_importances.txt"
                write "Saving feature importances to file [$fname]"
                writeFile fname, model.classifier.toString()
            }
        }

        featureImportances
    }

}
