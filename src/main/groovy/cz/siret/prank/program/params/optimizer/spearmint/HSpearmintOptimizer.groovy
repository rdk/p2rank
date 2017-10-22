package cz.siret.prank.program.params.optimizer.spearmint

import com.google.gson.Gson
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.optimizer.HObjectiveFunction
import cz.siret.prank.program.params.optimizer.HOptimizer
import cz.siret.prank.program.params.optimizer.HStep
import cz.siret.prank.program.params.optimizer.HVariable
import cz.siret.prank.utils.ATimer
import cz.siret.prank.utils.Formatter
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.ProcessRunner
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.nio.file.Path

import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Futils.*
import static cz.siret.prank.utils.Futils.delete

import static cz.siret.prank.utils.ProcessRunner.process

/**
 * optimizer based on https://github.com/HIPS/Spearmint
 */
@Slf4j
@CompileStatic
class HSpearmintOptimizer extends HOptimizer implements Writable {

    enum Likelihood { NOISELESS, GAUSSIAN }

    static int SLEEP_INTERVAL = 100

    String spearmintCommand = "python main.py"
    String mongodbCommand = "mongod"

    Likelihood likelihood = Likelihood.GAUSSIAN

    Path spearmintDir
    Path experimentDir

    /**
     * use absolute paths
     * @param spearmintDir
     * @param experimentDir
     */
    HSpearmintOptimizer(Path spearmintDir, Path experimentDir) {
        this.spearmintDir = spearmintDir
        this.experimentDir = experimentDir
    }

    @Override
    HStep optimize(HObjectiveFunction objective) {

        String dir = absSafePath( experimentDir.toString() )
        String varsDir = "$dir/vars"
        String evalDir = "$dir/eval"

        delete(dir)
        mkdirs(dir)
        mkdirs(varsDir)
        mkdirs(evalDir)
        writeFile "$dir/config.json", genConfig()
        writeFile "$dir/eval.py", genEval()

        String sysTmpDir = Futils.getSystemTempDir()
        String mongoDir = absSafePath("$sysTmpDir/prank/hopt/mongo")
        String mongoLogFile = absSafePath("$mongoDir/mongo.log" )
        String mongoOutFile = absSafePath("$mongoDir/mongo.out" )
        String mongoDataDir = absSafePath( "$mongoDir/data" )

        delete(mongoDir)
        mkdirs(mongoDataDir)

        try {
            write "Killing mongodb"
            process("sudo pkill mongo").inheritIO().executeAndWait()
        } catch (e) {
            log.error(e.message, e)
        }

        // run mongo
        write "Starting mongodb"
        String mcmd = "$mongodbCommand --fork --smallfiles --logpath $mongoLogFile --dbpath $mongoDataDir"
        write "  executing '$mcmd'"
        ProcessRunner mongoProc = process(mcmd, mongoDir).redirectErrorStream().redirectOutput(new File(mongoOutFile))
        int exitCode = mongoProc.executeAndWait()
        if (exitCode != 0) {
            log.error("Mongodb log: \n " + readFile(mongoLogFile))
            throw new PrankException("Failed to execute mongodb (required by spearmint)")
        }


        // run spearmint
        write "Starting spearmint"
       // String scmd = spearmintCommand + " " + dir
        String scmd = spearmintCommand + " " + dir
//        ProcessRunner spearmintProc = new ProcessRunner(scmd, spearmintDir.toString()).redirectErrorStream().redirectOutput(new File("$dir/spearmint.out"))
        write "  executing '$scmd'"
        ProcessRunner spearmintProc = process(scmd, spearmintDir.toString()).inheritIO()
        spearmintProc.execute()

        int stepNumber = 0
        int jobId          // spearmint job id, may start at any number

        // find starting speramint job id

        log.debug "waiting for first vars file in '$varsDir'"
        while (isDirEmpty(varsDir)) {
            log.debug "waiting..."
            sleep(SLEEP_INTERVAL)
        }
        jobId = listFiles(varsDir).first().name.toInteger()

        List<String> varNames = (variables*.name).toList()
        String stepsf = "$dir/steps.csv"
        writeFile stepsf, "[num], [job_id], " + varNames.join(", ") + ", [value] \n"

        double sumTime = 0

        while (stepNumber < maxIterations) {
            def timer = startTimer()
            log.info "job id: {}", jobId
            String varf = "$varsDir/$jobId"
            waitForFile(varf)

            
            // parse variable assignment
            Map<String, Object> vars = new Gson().fromJson(readFile(varf), Map.class);
            log.info "vars: {}", vars

            // eval objective function
            double val = objective.eval(vars, stepNumber)
            log.info "value: {}", val
            writeFile "$evalDir/$jobId", formatValue(val)

            HStep step = new HStep(stepNumber, vars, val)
            steps.add(step)
            append stepsf, "$stepNumber, $jobId, " + varNames.collect { fmt vars.get(it) }.join(", ") + ", ${fmt val} \n"
            String bests = printBestStep(bestStep, varNames)
            writeFile "$dir/best.csv", bests
            write "BEST STEP:\n" + bests
            write "For results see " + stepsf

            long time = timer.timeSec
            sumTime += time
            long avgTime = (long)(sumTime / (stepNumber+1))
            write "Step $stepNumber finished in ${time}s (avg: ${avgTime}s)"

            stepNumber++
            jobId++
        }

        spearmintProc.kill()
        mongoProc.kill()

        return getBestStep()
    }

    String printBestStep(HStep step, List<String> varNames) {
        varNames.collect { it + ",\t\t" + fmt(step.variableValues.get(it)) }.join("\n") + "\nvalue,\t\t" + fmt(step.functionValue) + "\n"
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
            sleep(SLEEP_INTERVAL)
        } 
    }

    private String genConfig() {

        """
        {
            "language"        : "PYTHON",
            "main-file"       : "eval.py",
            "experiment-name" : "spearmint_experiment",
            "likelihood"      : "$likelihood",
            "variables" : {
                ${genVariables()}
            }
        }
        """

    }

    private String genVariable(HVariable var) {
        """
                "$var.name" : {
                    "type" : "$var.type",
                    "size" : 1,
                    "min"  : $var.min,
                    "max"  : $var.max
                }
        """
    }

    private String genVariables() {
        variables.collect { genVariable(it) }.join(",\n")
    }

    private String genEval() {
        """
import numpy as np
import math
import os.path
import time
import json
import os

def eval(job_id, variables):

    valf = "eval/" + str(job_id)

    # wait until value file exists
    while not os.path.exists(valf):
        time.sleep(1)

    with open(valf) as file:
        value = file.read()
        
    return float(value)

def main(job_id, variables):
    print "variables: " + str(variables)

    # prepare vars for java
    vars = {}
    for key, value in variables.iteritems():
        vars[key] = value[0]
    print "vars: " + json.dumps(vars)
    if not os.path.exists("vars"):
        os.makedirs("vars")
    varf = "vars/" + str(job_id) 
    with open(varf, "w") as file:
        file.write(str(json.dumps(vars)) + "\\n")

    return eval(job_id, variables)
    
        """
    }

    /*
$ tree
.
├── branin.py
├── config.json
├── eval
│   ├── 78
│   └── 79
├── output
│   ├── 00000078.out
│   ├── 00000079.out
│   └── 00000080.out
└── vars
    ├── 78.json
    ├── 79.json
    └── 80.json
     */

}
