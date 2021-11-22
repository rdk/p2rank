package cz.siret.prank.program.routines.optimize

import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.ListParam
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.params.optimizer.HObjectiveFunction
import cz.siret.prank.program.params.optimizer.HOptimizer
import cz.siret.prank.program.params.optimizer.HVariable
import cz.siret.prank.program.params.optimizer.bayesian.HPyGpgoOptimizer
import cz.siret.prank.program.params.optimizer.bayesian.HSpearmintOptimizer
import cz.siret.prank.program.routines.optimize.ParamLooper.ParamVal
import cz.siret.prank.program.routines.optimize.ParamLooper.Step
import cz.siret.prank.program.routines.results.EvalResults
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.Futils.absSafePath

/**
 * Hyperparameter optimizer routine
 */
@Slf4j
@CompileStatic
class HyperOptimizerRoutine extends ParamLooper {

    static final String HOPT_OBJECTIVE = "HOPT_OBJECTIVE"

    final String objectiveParam = params.hopt_objective

    List<ListParam> listParams

    HyperOptimizerRoutine(String outdir, List<ListParam> listParams) {
        super(outdir)
        this.listParams = listParams
    }

    @CompileDynamic
    private HVariable convertListParam(ListParam p) {
        assert p.values.size() == 2

        String name = p.name

        HVariable.Type type = HVariable.Type.FLOAT
        Object paramVal = Params.inst."$name"
        if (paramVal instanceof Integer || paramVal instanceof Boolean) {
            type = HVariable.Type.INT
        }

        Number min = Double.parseDouble p.values[0].toString()
        Number max = Double.parseDouble p.values[1].toString()

        new HVariable(name, type, min, max)
    }


    private List<HVariable> convertListParams(List<ListParam> listParams) {
        listParams.collect { convertListParam(it) }.toList()
    }

    void optimizeParameters(Closure<EvalResults> evalClosure) {

        log.info "List variables: " + listParams.toListString()
        List<HVariable> variables = convertListParams(listParams)
        log.info "Variables: " + variables.toListString()

        HOptimizer optimizer = createOptimizer(variables)

        optimizer.optimize(new HObjectiveFunction() {
            @Override
            double eval(Map<String, Object> variableValues, int stepNumber) {

                List<ParamVal> paramVals = variables.collect { new ParamVal(name: it.name, value: variableValues.get(it.name)) }.toList()
                Step step = new Step(params: paramVals)
                steps.add(step)

                double val

                try {
                    Closure<EvalResults> calcObjectiveWrapper = { String stepDir ->
                        EvalResults res = evalClosure.call(stepDir)
                        double objective = getObjectiveValue(res, objectiveParam)
                        // we want to calc objective before calling processStep
                        // that will save it to selected_stats table
                        res.additionalStats.put((String)HOPT_OBJECTIVE, objective)
                        return res
                    }
                    if (!params.selected_stats.contains(HOPT_OBJECTIVE)) {
                        params.selected_stats.add(HOPT_OBJECTIVE as String)
                    }
                    EvalResults res = processStep(step, "step.$stepNumber", calcObjectiveWrapper)

                    val = res.additionalStats.get(HOPT_OBJECTIVE)
                    //TODO: write selected stats file sorted by hopt_objective

                } catch (Exception e) {
                    log.error("Couldn't process hyperparameter optimization step $stepNumber", e)
                    val = Double.NaN
                }

                return val
            }
        })
    }

    static double getObjectiveValue(EvalResults res, String objectiveParam) {
        String name = objectiveParam
        double sign = 1
        if (name.startsWith("-")) {
            name = name.substring(1)
            sign = -1
        }
        
        Double val = (Double) res.stats.get(name)
        if (val == null) {
            throw new PrankException("Invalid hopt_objective. Metric with name '$name' not found.")
        }
        
        return sign * (double)res.stats.get(name)
    }

    private HOptimizer createOptimizer(List<HVariable> variables) {

        // possible create other optimizers than Spearmint here

        HOptimizer opt;
        String hoptDir = absSafePath("$outdir/hopt")

        String optName = params.hopt_optimizer

        if ("spearmint" == optName) {
            opt = new HSpearmintOptimizer(hoptDir, absSafePath(params.hopt_spearmint_dir))
        } else if ("pygpgo" == optName) {
            opt = new HPyGpgoOptimizer(hoptDir)
        } else {
            throw new PrankException("Invalid value of hopt_optimizer parameter: '$optName'")
        }

        opt.withObjectiveLabel(params.hopt_objective)
        opt.withMaxIterations(params.hopt_max_iterations)
        opt.withVariables(variables)
        
        return opt
    }

}
