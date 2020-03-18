package cz.siret.prank.program.ml

import groovy.transform.CompileStatic
import weka.classifiers.functions.Logistic
import weka.classifiers.functions.SimpleLogistic
import weka.classifiers.meta.Bagging

@CompileStatic
public enum ClassifierOption {

    RandomForest,
    AdaBoostM1_RF,
    CostSensitive_RF,
    FastRandomForest,
    FasterForest,
    FasterForest2,
    SmileRandomForest,
    Bagging,
    SimpleLogistic,
    Logistic,
    Stack1

}