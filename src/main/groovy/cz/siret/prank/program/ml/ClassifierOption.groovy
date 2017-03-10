package cz.siret.prank.program.ml

import groovy.transform.CompileStatic

@CompileStatic
public enum ClassifierOption {

    RandomForest,
    AdaBoostM1_RF,
    CostSensitive_RF,
    FastRandomForest,
    Bagging,
    SimpleLogistic,
    Logistic,
    Stack1

}