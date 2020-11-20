package cz.siret.prank.program.ml

import groovy.transform.CompileStatic

@CompileStatic
enum ClassifierOption {

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