package cz.siret.prank.program.routines

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import hr.irb.fastRandomForest.FastRandomForest
import cz.siret.prank.domain.Dataset
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.program.params.ClassifierOption
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.ATimer
import cz.siret.prank.utils.CSV
import cz.siret.prank.utils.WekaUtils
import cz.siret.prank.utils.futils
import weka.classifiers.Classifier
import weka.classifiers.CostMatrix
import weka.classifiers.functions.Logistic
import weka.classifiers.functions.MultilayerPerceptron
import weka.classifiers.functions.SimpleLogistic
import weka.classifiers.meta.AdaBoostM1
import weka.classifiers.meta.Bagging
import weka.classifiers.meta.CostSensitiveClassifier
import weka.classifiers.meta.Stacking
import weka.classifiers.trees.RandomForest
import weka.core.Instances

@Slf4j
class TrainEvalIteration extends CompositeRoutine implements Parametrized  {

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

    EvaluateRoutine evalRoutine

    Results execute() {

        collectTrainVectors()
        Results res = trainAndEvalModel()

        if (deleteVectors)
            deleteVectorFiles()

        return res
    }

    void collectTrainVectors() {
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
        ATimer timer = ATimer.start();

        new File(outdir).mkdirs()

        CollectVectorsRoutine collector = new CollectVectorsRoutine(dataSet, outdir, vectFileName)

        def res = collector.collectVectors()
        Instances inst = res.instances
        train_positives = res.positives
        train_negatives = res.negatives

        logTime "vectors collected in " + timer.formatted

        return inst
    }

    void deleteVectorFiles() {
        futils.delete(trainVectorFile)
        futils.delete(evalVectorFile)
    }

    Results trainAndEvalModel() {
        def timer = ATimer.start()

        new File(outdir).mkdirs()

        Classifier classifier = createClassifier()
        String classifierLabel = "${classifier.class.simpleName}_${label}"
        String modelf = "$outdir/${classifierLabel}.model"

        if (trainVectors==null) {
            trainVectors = WekaUtils.loadData(trainVectorFile)
        }

        write "training classifier ${classifier.getClass().name} on dataset with ${trainVectors.size()} instances"

        WekaUtils.trainClassifier(classifier, trainVectors)
        long trainTime = timer.time
        if (!params.delete_models) {
            WekaUtils.saveClassifier(classifier, modelf)
            write "model saved to file $modelf (${futils.sizeMBFormatted(modelf)} MB)"
        }

        List<Double> featureImportances

        // feature importances
        if (params.feature_importances) {
            if (classifier instanceof  FastRandomForest) {
                featureImportances = (classifier as FastRandomForest).featureImportances.toList()
            } else if (classifier instanceof FasterForest) {
                featureImportances = (classifier as FasterForest).featureImportances.toList()
            }
            if (featureImportances != null) {
                List<String> names = FeatureExtractor.createFactory().vectorHeader
                featureImportances = (classifier as FastRandomForest).featureImportances.toList()
                List<String> names = FeatureExtractor.createFactory().vectorHeader

                Writer file = futils.overwrite("$outdir/feature_importances.csv")
                file << names.join(',') << "\n"
                file << CSV.fromDoubles(featureImportances) << "\n"
                file.close()
                // TODO: calculate variance of feature importances over seedloop iterations
            }
        }

        logTime "model trained in " + timer.formatted

        timer.restart()

        evalRoutine = new EvaluateRoutine(evalDataSet, classifier, classifierLabel, outdir)
        Results res = evalRoutine.execute()
        res.trainTime = trainTime
        res.train_positives = train_positives
        res.train_negatives = train_negatives
        res.featureImportances = featureImportances

        logTime "evaluation routine on dataset [$evalDataSet.name] finished in " + timer.formatted

        if (deleteModel) futils.delete(modelf)

        return res
    }

    private getRfThreads() {
        int nt = params.threads
        if (params.rf_threads>0) {
            nt = params.rf_threads
        }
        write "training model using $nt threads"
        return nt
    }

    Classifier createRandomForest() {
        RandomForest cs = new RandomForest()
        cs.with {
            maxDepth = params.rf_depth
            numIterations = params.rf_trees
            numFeatures = params.rf_features
            seed = params.seed
            numExecutionSlots = getRfThreads()
        }
        return cs
    }

    FastRandomForest createFastRandomForest() {
        FastRandomForest cs = new FastRandomForest()
        cs.with {
            maxDepth = params.rf_depth
            numTrees = params.rf_trees
            numFeatures = params.rf_features
            seed = params.seed
            numThreads = getRfThreads()
            computeImportances = params.feature_importances
        }
        return cs
    }

    @CompileStatic
    Classifier createClassifier() {

        ClassifierOption option = ClassifierOption.valueOf(params.classifier)

        switch (option) {

            case ClassifierOption.RandomForest:
                return createRandomForest()

            case ClassifierOption.FastRandomForest:
                return createFastRandomForest()

            case ClassifierOption.AdaBoostM1_RF:
                AdaBoostM1 cs = new AdaBoostM1()
                cs.setNumIterations(params.meta_classifier_iterations)
                cs.setSeed(params.seed)
                cs.setClassifier(createFastRandomForest())
                return cs

            case ClassifierOption.CostSensitive_RF:
                CostSensitiveClassifier cs = new CostSensitiveClassifier()
                double cost = params.false_positive_cost
                CostMatrix matrix = CostMatrix.parseMatlab("[0 $cost; 1 0]")
                cs.setCostMatrix(matrix)
                cs.setMinimizeExpectedCost(false)
                cs.setSeed(params.seed)
                cs.setClassifier(createRandomForest())
                return cs

            case ClassifierOption.Bagging:
                Bagging cs = new Bagging();
                double bagsize = 100/params.threads
                cs.setBagSizePercent((int)bagsize)
                cs.setNumExecutionSlots(params.threads)
                cs.setNumIterations(10)
                cs.setSeed(params.seed)
                //cs.setCalcOutOfBag(true)

                FastRandomForest rf = createFastRandomForest()
                rf.numThreads = 1
                cs.setClassifier(rf)

                return cs

            case ClassifierOption.SimpleLogistic:
                SimpleLogistic cs = new SimpleLogistic()
                cs.setErrorOnProbabilities(true)
                return cs

            case ClassifierOption.Logistic:
                Logistic cs = new Logistic()
                return cs

            case ClassifierOption.Stack1:
                Stacking st = new Stacking();
                st.setNumFolds(5)
                st.setNumExecutionSlots(params.threads)
                st.setSeed(params.seed)
                st.setMetaClassifier(new Logistic())

                Classifier[] cls = [new Logistic(), createFastRandomForest(), new MultilayerPerceptron()] as Classifier[]
                st.setClassifiers(cls)

                return st
        }

    }

}
