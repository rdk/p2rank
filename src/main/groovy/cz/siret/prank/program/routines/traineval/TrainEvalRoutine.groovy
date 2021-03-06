package cz.siret.prank.program.routines.traineval

import cz.siret.prank.domain.Dataset
import cz.siret.prank.prediction.metrics.ClassifierStats
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
import weka.core.Instances

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
        this.trainDataSet = Objects.requireNonNull(trainData, "Training dataset was not provided. Run with '-t {train_dataset}.ds'")
        this.evalDataSet = Objects.requireNonNull(evalData, "Evaluation dataset was not provided. Run with '-e {eval_dataset}.ds'")
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
                double score = normalizedScore(hist)
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
        if (params.hopt_train_only_once) {
            return !ALTERADY_TRAINED
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

        evalRoutine = EvalRoutine.create(params.predict_residues, evalDataSet, model, outdir)

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
