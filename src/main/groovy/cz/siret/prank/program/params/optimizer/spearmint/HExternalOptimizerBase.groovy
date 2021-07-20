package cz.siret.prank.program.params.optimizer.spearmint

import com.google.gson.Gson
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.optimizer.HObjectiveFunction
import cz.siret.prank.program.params.optimizer.HOptimizer
import cz.siret.prank.program.params.optimizer.HStep
import cz.siret.prank.utils.Formatter
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Futils.*

/**
 * Base class for running external process optimizers
 * communicating with P2Rank via filesystem.
 */
@Slf4j
@CompileStatic
abstract class HExternalOptimizerBase extends HOptimizer implements Parametrized, Writable {

    protected int sleepInterval = 100
    protected String experimentDir

    HExternalOptimizerBase(String experimentDir) {
        this.experimentDir = experimentDir
    }


    abstract void start(String varsDir, String evalDir)
    abstract void finalizeAndCleanup()


    @Override
    HStep optimize(HObjectiveFunction objective) {

        String dir = experimentDir
        String varsDir = "$dir/vars"
        String evalDir = "$dir/eval"
        delete(dir)
        mkdirs(dir)
        mkdirs(varsDir)
        mkdirs(evalDir)

        start(varsDir, evalDir)

        try {
            int stepNumber = 0
            int jobId          // spearmint job id, may start at any number

            // find first starting job id
            log.debug "waiting for first file with generated variables in '$varsDir'"
            while (isDirEmpty(varsDir)) {
                log.debug "waiting..."
                sleep(sleepInterval)
            }
            jobId = listFiles(varsDir).first().name.toInteger()

            List<String> varNames = (variables*.name).toList()
            String stepsf = "$dir/steps.csv"
            writeFile stepsf, "[num], [job_id], " + varNames.join(", ") + ", [value], [best_so_far] \n"

            double sumTime = 0

            while (stepNumber < maxIterations) {
                def timer = startTimer()
                log.info "job id: {}", jobId
                String varf = "$varsDir/$jobId"
                waitForFile(varf)

                // parse variable assignment
                Map<String, Object> vars = new Gson().fromJson(readFile(varf), Map.class)
                log.info "vars: {}", vars

                // eval objective function
                double val = objective.eval(vars, stepNumber)
                log.info "value: {}", val
                // -val because optimizers are by default minimizing and we want to maximize
                writeFile "$evalDir/$jobId", formatValue(-val)

                HStep step = new HStep(stepNumber, vars, val)
                steps.add(step)
                HStep bestStep = getBestStep()

                append stepsf, "$stepNumber, $jobId, " + varNames.collect { fmt vars.get(it) }.join(", ") + ", ${fmt val}, ${fmt bestStep.objectiveValue} \n"
                String bestCsv = printBestStepCsv(bestStep, varNames)
                writeFile "$dir/best.csv", bestCsv
                write "BEST STEP:\n" + bestCsv
                write "For results see " + stepsf

                long time = timer.timeSec
                sumTime += time
                long avgTime = (long)(sumTime / (stepNumber+1))
                write "Step $stepNumber finished in ${time}s (avg: ${avgTime}s)"

                stepNumber++
                jobId++
            }
        } catch (Exception e) {
            throw new PrankException("Hyperparameter optimiation failed.", e)
        } finally {
            finalizeAndCleanup()
        }

        return getBestStep()
    }

    String printBestStepCsv(HStep step, List<String> varNames) {
        varNames.collect { it + ",\t\t" + fmt(step.variableValues.get(it)) }.join("\n") + "\nvalue ($objectiveLabel),\t\t" + fmt(step.objectiveValue) + "\n"
    }

    String formatValue(double v) {
        Formatter.format(v, 5)
    }

    static String fmt(Object x) {
        if (x==null) return ""
        sprintf "%8.4f", x
    }

    void waitForFile(String fname) {
        log.info "waiting for file '$fname'"
        while (!exists(fname)) {
            log.debug "waiting..."
            sleep(sleepInterval)
        }
    }

    
}
