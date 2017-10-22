package cz.siret.prank.program.params.optimizer

import groovy.transform.CompileStatic

/**
 *
 */
@CompileStatic
abstract class HOptimizer {

    List<HVariable> variables
    int maxIterations = 100

    protected List<HStep> steps = new ArrayList<>()


    HOptimizer withVariables(List<HVariable> variables) {
        this.variables = variables
        this
    }

    HOptimizer withMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations
        this
    }

    List<HStep> getSteps() {
        return steps
    }

    HStep getBestStep() {
        assert !steps.isEmpty()

        steps.min { it.functionValue }
    }

    /**
     * Maximize the objective function value
     * @return best step
     */
    public abstract HStep optimize(HObjectiveFunction function);


}
