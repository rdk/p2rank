package cz.siret.prank.program.params.optimizer

import groovy.transform.CompileStatic

/**
 * Base class for hyperparameter optimizers
 */
@CompileStatic
abstract class HOptimizer {

    List<HVariable> variables
    int maxIterations = 100
    String objectiveLabel

    protected List<HStep> steps = new ArrayList<>()


    HOptimizer withVariables(List<HVariable> variables) {
        this.variables = variables
        this
    }

    HOptimizer withMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations
        this
    }

    HOptimizer withObjectiveLabel(String objectiveLabel) {
        this.objectiveLabel = objectiveLabel
        this
    }

    List<HStep> getSteps() {
        return steps
    }

    HStep getBestStep() {
        assert !steps.isEmpty()

        return steps.max {it.objectiveValue }
    }

    /**
     * Maximize the objective function value
     * @return best step
     */
    abstract HStep optimize(HObjectiveFunction function);

}
