package cz.siret.prank.program.params.optimizer

import groovy.transform.CompileStatic

/**
 *
 */
@CompileStatic
class HStep {

    int number
    Map<String, Object> variableValues
    double functionValue

    HStep(int number, Map<String, Object> variableValues, double functionValue) {
        this.number = number
        this.variableValues = variableValues
        this.functionValue = functionValue
    }
}
