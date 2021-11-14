package cz.siret.prank.program.params.optimizer.bayesian

import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.optimizer.HVariable
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.ProcessRunner
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.Futils.*
import static cz.siret.prank.utils.ProcessRunner.process

/**
 * Optimizer based on https://github.com/HIPS/Spearmint
 */
@Slf4j
@CompileStatic
class HSpearmintOptimizer extends HExternalOptimizerBase {

    enum Likelihood { NOISELESS, GAUSSIAN }

    String spearmintCommand = "$params.hopt_python_command main.py"
    String mongodbCommand = "mongod"

    Likelihood likelihood = Likelihood.GAUSSIAN

    String spearmintDir

    ProcessRunner mongoProc
    ProcessRunner spearmintProc

//===========================================================================================================//

    HSpearmintOptimizer(String experimentDir, String spearmintDir) {
        super(experimentDir)
        this.spearmintDir = spearmintDir
    }

    @Override
    void start(String varsDir, String evalDir) {
        String dir = experimentDir

        writeFile "$dir/config.json", genConfig()
        writeFile "$dir/eval.py", genEvalCode()

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
        write "  executing '$mcmd' in '$mongoDir'"
        mongoProc = process(mcmd, mongoDir).redirectErrorStream().redirectOutput(new File(mongoOutFile))
        int exitCode = mongoProc.executeAndWait()
        if (exitCode != 0) {
            log.error("Mongodb log: \n " + readFile(mongoLogFile))
            throw new PrankException("Failed to execute mongodb (required by spearmint)")
        }

        // run spearmint
        write "Starting spearmint"
        String scmd = spearmintCommand + " " + dir
        write "  executing '$scmd' in '$spearmintDir'"
        spearmintProc = process(scmd, spearmintDir).inheritIO()
        spearmintProc.execute()
    }

    @Override
    void finalizeAndCleanup() {
        spearmintProc.kill()
        mongoProc.kill()
    }

//===========================================================================================================//

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

    private String genVariable(HVariable v) {
        """
                "$v.name" : {
                    "type" : "$v.type",
                    "size" : 1,
                    "min"  : $v.min,
                    "max"  : $v.max
                }
        """
    }

    private String genVariables() {
        variables.collect { genVariable(it) }.join(",\n")
    }

    private String genEvalCode() {
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
├── eval.py
├── config.json
├── eval
│  ├── 78
│  └── 79
├── output
│   ├── 00000078.out
│   ├── 00000079.out
│   └── 00000080.out
└── vars
    ├── 78.json
    ├── 79.json
    └── 80.json
 */

}
