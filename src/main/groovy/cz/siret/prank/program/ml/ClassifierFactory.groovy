package cz.siret.prank.program.ml

import cz.siret.prank.fforest.FasterForest
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import hr.irb.fastRandomForest.FastRandomForest
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

/**
 *
 */
@CompileStatic
class ClassifierFactory implements Parametrized, Writable {

    Params params

    ClassifierFactory(Params params) {
        this.params = params
    }

    int getRfThreads() {
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

    FasterForest createFasterForest() {
        FasterForest cs = new FasterForest()
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


    Classifier createClassifier() {

        ClassifierOption option = ClassifierOption.valueOf(params.classifier)

        switch (option) {

            case ClassifierOption.RandomForest:
                return createRandomForest()

            case ClassifierOption.FastRandomForest:
                return createFastRandomForest()

            case ClassifierOption.FasterForest:
                return createFasterForest()

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

    static Classifier createClassifier(Params params) {
        new ClassifierFactory(params).createClassifier()
    }

}
