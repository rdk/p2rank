package cz.siret.prank.program.params.optimizer.bayesian


import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.optimizer.HObjectiveFunction
import cz.siret.prank.program.params.optimizer.HOptimizer
import cz.siret.prank.program.params.optimizer.HStep
import cz.siret.prank.utils.Formatter
import cz.siret.prank.utils.Sutils
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
            writeFile stepsf, "[num], [job_id], [value], [best_so_far], [time_s], " + varNames.join(", ") + "\n"

            double sumTime = 0
            long lastoptimizerWaitingTimeSec = 0

            while (stepNumber < maxIterations) {
                def timer = startTimer()
                log.info "job id: {}", jobId
                String varf = "$varsDir/$jobId"
                write "Waiting for the optimizer to produce new variable assignment (last iteration: ${lastoptimizerWaitingTimeSec}s)."
                lastoptimizerWaitingTimeSec = waitForFile(varf) 

                // parse variable assignment

                Map<String, Object> vars = Sutils.parseJson(readFile(varf), Map.class)
                log.info "vars: {}", vars

                // eval objective function

                double objVal = objective.eval(vars, stepNumber)
                log.info "value: {}", objVal
                // -val because optimizers are by default minimizing and we want to maximize
                writeFile "$evalDir/$jobId", formatValue(-objVal)

                // log result and best

                HStep step = new HStep(stepNumber, vars, objVal)
                steps.add(step)
                HStep bestStep = getBestStep()

                long time = timer.timeSec

                append(stepsf, "$stepNumber, $jobId, ${fmt objVal}, ${fmt bestStep.objectiveValue}, $time, "
                    + varNames.collect { fmt vars.get(it) }.join(", ") + " \n")
                String bestCsv = printBestStepCsv(bestStep, varNames)
                writeFile "$dir/best.csv", bestCsv
                write "BEST STEP:\n" + bestCsv
                write "For results see " + stepsf

                sumTime += time
                long avgTime = (long)(sumTime / (stepNumber+1))
                write "Step $stepNumber finished in ${time}s (avg: ${avgTime}s)"

                stepNumber++
                jobId++
            }
        } catch (Exception e) {
            throw new PrankException("Hyperparameter optimization failed.", e)
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

    /**
     * Wait fot file to be created.
     * @return waiting time in seconds
     */
    long waitForFile(String fname) {
        def timer = startTimer()
        log.info "waiting for file '$fname'"
        while (!exists(fname)) {
            log.debug "waiting..."
            sleep(sleepInterval)
        }
        timer.timeSec
    }
    
}
