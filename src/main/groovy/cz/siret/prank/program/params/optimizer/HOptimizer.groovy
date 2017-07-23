package cz.siret.prank.program.params.optimizer
/**
 *
 */
abstract class HOptimizer {

    List<HVariable> params
    int maxIterations = 100

    protected List<HStep> steps = new ArrayList<>()


    HOptimizer withParams(List<HVariable> params) {
        this.params = params
        this
    }

    HOptimizer withMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations
        this
    }

    List<HStep> getSteps() {
        return steps
    }

    /**
     * @return best step
     */
    abstract HStep optimize(HObjectiveFunction objective);


}
