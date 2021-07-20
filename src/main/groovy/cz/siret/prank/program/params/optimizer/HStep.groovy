package cz.siret.prank.program.params.optimizer

import groovy.transform.CompileStatic

/**
 *
 */
@CompileStatic
class HStep {

    int number
    Map<String, Object> variableValues
    double objectiveValue

    HStep(int number, Map<String, Object> variableValues, double objectiveValue) {
        this.number = number
        this.variableValues = variableValues
        this.objectiveValue = objectiveValue
    }
}
