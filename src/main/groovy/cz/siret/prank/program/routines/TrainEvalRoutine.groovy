package cz.siret.prank.program.routines

import cz.siret.prank.domain.Dataset
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.prediction.metrics.ClassifierStats
import cz.siret.prank.utils.ATimer
import cz.siret.prank.utils.CSV
import cz.siret.prank.utils.Formatter
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.WekaUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import weka.classifiers.Classifier
import weka.core.Instance
import weka.core.Instances

import static cz.siret.prank.prediction.pockets.PointScoreCalculator.applyPointScoreThreshold
import static cz.siret.prank.prediction.pockets.PointScoreCalculator.predictedScore
import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Formatter.formatTime
import static cz.siret.prank.utils.Futils.mkdirs

@Slf4j
@CompileStatic
class TrainEvalRoutine extends EvalRoutine implements Parametrized  {

    Dataset trainDataSet
    Dataset evalDataSet
    String label

    boolean deleteModel = params.delete_models
    boolean deleteVectors = params.delete_vectors

    Instances trainVectors

    int train_positives
    int train_negatives

    // may be same between iterations
    private String trainVectorFile
    private String evalVectorFile

    EvalRoutine evalRoutine
    static Model staticModel = null

    TrainEvalRoutine(String outdir, Dataset trainData, Dataset evalData) {
        super(outdir)
        this.trainDataSet = Objects.requireNonNull(trainData, "Training dataset must be provided.")
        this.evalDataSet = Objects.requireNonNull(evalData, "Evaluation dataset must be provided.")
    }

    EvalResults execute() {

        collectTrainVectors()
        EvalResults res = trainAndEvalModel()

        if (deleteVectors)
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

    private Instances doCollectVectors(Dataset dataSet, String vectFileName) {
        ATimer timer = startTimer()

        mkdirs(outdir)

        CollectVectorsRoutine collector = new CollectVectorsRoutine(dataSet, outdir, vectFileName)

        def res = collector.collectVectors()
        Instances inst = res.instances
        train_positives = res.positives
        train_negatives = res.negatives

        logTime "vectors collected in " + timer.formatted

        return inst
    }

    void deleteVectorFiles() {
        Futils.delete(trainVectorFile)
        Futils.delete(evalVectorFile)
    }

    ClassifierStats calculateTrainStats(Classifier classifier, Instances trainVectors) {
        if (params.classifier_train_stats) {
            ClassifierStats trainStats = new ClassifierStats()
            for (Instance inst : trainVectors) {
                double[] hist = classifier.distributionForInstance(inst)
                double score = predictedScore(hist)
                boolean predicted = applyPointScoreThreshold(score)
                boolean observed = inst.classValue() > 0

                trainStats.addPrediction(observed, predicted, score, hist)
            }
            return trainStats
        } else {
            return null
        }
    }

    private static boolean ALTERADY_TRAINED = false

    boolean shouldTrainModel() {
//        if (evalDataSet.hasPredictedResidueLabeling()) {
//            return false
//        }

        if (params.hopt_train_only_once) {
            if (ALTERADY_TRAINED) {
                return false
            } else {
                return true
            }
        } else {
            true
        }
    }

    EvalResults trainAndEvalModel() {
        def timer = startTimer()

        mkdirs(outdir)

        // TODO perform subsampling or supersampling if needed
        // -> move subsampling/supersampling logic from cz.siret.prank.collectors.DataPreprocessor.preProcessTrainData
        // here so befire every seedloop run there is new random sampling

        long trainTime = 0
        ClassifierStats trainStats = null
        List<Double> featureImportances = null
        String modelf = null
        Model model = null

        if (shouldTrainModel()) {
            model = Model.createNewFromParams(params)
            modelf = "$outdir/${model.label}.model"

            if (trainVectors==null) {
                trainVectors = WekaUtils.loadData(trainVectorFile)
            }

            write "training classifier ${model.classifier.getClass().name} on dataset with ${trainVectors.size()} instances"

            def trainTimer = startTimer()
            WekaUtils.trainClassifier(model.classifier, trainVectors)
            trainTime = trainTimer.time
            logTime "model trained in " + formatTime(trainTime)

            if (!params.delete_models) {
                model.saveToFile(modelf)
            }
            trainStats = calculateTrainStats(model.classifier, trainVectors)
            featureImportances = calcFeatureImportances(model)

            if (params.hopt_train_only_once) {
                ALTERADY_TRAINED = true
                staticModel = model
            }
        } else {
            model = staticModel
        }

        logTime "model prepared in " + timer.formatted
        timer.restart()

        if (params.predict_residues) {
            evalRoutine = new EvalResiduesRoutine(evalDataSet, model, outdir)
        } else {
            evalRoutine = new EvalPocketsRoutine(evalDataSet, model, outdir)
        }

        EvalResults res = evalRoutine.execute()
        res.totalTrainingTime = trainTime
        res.train_positives = train_positives
        res.train_negatives = train_negatives
        res.featureImportances = featureImportances
        res.classifierTrainStats = trainStats


        logTime "evaluation routine on dataset [$evalDataSet.name] finished in " + timer.formatted

        if (deleteModel)
            Futils.delete(modelf)

        return res
    }

    private List<Double> calcFeatureImportances(Model model) {
        List<Double> featureImportances = null
        if (params.feature_importances && model.hasFeatureImportances()) {
            featureImportances = model.featureImportances
            if (featureImportances != null) {
                List<String> names = FeatureExtractor.createFactory().vectorHeader

                Writer file = Futils.getWriter("$outdir/feature_importances.csv")
                file << names.join(',') << "\n"
                file << CSV.fromDoubles(featureImportances) << "\n"
                file.close()
                // TODO: calculate variance of feature importances over seedloop iterations
            }
        }
        featureImportances
    }


}
