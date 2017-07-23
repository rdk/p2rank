package cz.siret.prank.program.params.optimizer.spearmint

import cz.siret.prank.program.params.optimizer.HObjectiveFunction
import cz.siret.prank.program.params.optimizer.HOptimizer
import cz.siret.prank.program.params.optimizer.HStep
import cz.siret.prank.program.params.optimizer.HVariable

import java.nio.file.Path

/**
 * optimizer based on https://github.com/HIPS/Spearmint
 */
class HSpearmintOptimizer extends HOptimizer {

    enum Likelihood { NOISELESS, GAUSSIAN }

    String spearmintCommand = "spearmint"
    String mongodbCommand = "mongodb"

    Likelihood likelihood = Likelihood.GAUSSIAN

    Path experimentDir
    


    @Override
    HStep optimize(HObjectiveFunction objective) {
        // run mongo
        // run spearmint

        int stepNumber = 0

        while (true) {


            if (stepNumber>maxIterations) {
                break
            }
        }

        // stop spearmint
        // stop mongo
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
        params.collect { genVariable(it) }.join(",\n")
    }

    private String genEval() {
        """
import numpy as np
import math
import os.path
import time
import json
import os

def eval(job_id, params):

    valf = "eval/" + str(job_id)

    # wait until value file exists
    while not os.path.exists(valf):
        time.sleep(1)

    with open(valf) as file:
        value = file.read()
        
    return float(value)

def main(job_id, params):
    print "params: " + str(params)

    # prepare vars for java
    vars = {}
    for key, value in params.iteritems():
        vars[key] = value[0]
    print "vars: " + json.dumps(vars)
    if not os.path.exists("vars"):
        os.makedirs("vars")
    varf = "vars/" + str(job_id) + ".json"
    with open(varf, "w") as file:
        file.write(json.dumps(vars))

    return eval(job_id, params)
    
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
