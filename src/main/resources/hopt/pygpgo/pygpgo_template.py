import numpy as np
import math
import time
import json
import os
import os.path
import pyGPGO
from pyGPGO.covfunc import matern32
from pyGPGO.acquisition import Acquisition
from pyGPGO.surrogates.GaussianProcess import GaussianProcess
from pyGPGO.GPGO import GPGO
#from pyGPGO.surrogates.GaussianProcessMCMC import GaussianProcessMCMC
#import pymc3 as pm


# store vars as json to vars/{job_id}
def store_vars(job_id, vars):
    print("vars: ", str(vars))
    vars_json = json.dumps(vars)
    print("vars_json: ", vars_json)
    varf = "vars/" + str(job_id)
    print("saving vars to file: ", varf)

    if not os.path.exists("vars"):
        os.makedirs("vars")
    with open(varf, "w") as file:
        file.write(str(json.dumps(vars)) + "\n")


# wait and read objective value from file eval/{job_id}
def read_eval(job_id):
    valf = "eval/" + str(job_id)
    print("reading eval from file: ", valf)

    # wait until value file exists
    while not os.path.exists(valf):
        time.sleep(1)
    with open(valf) as file:
        value = file.read()

    return float(value)


def objective(**kwargs):
    global job_id
    job_id += 1
        
    store_vars(job_id, kwargs)
    return read_eval(job_id)


param_seed = @@param.seed@@
param_max_iters = @@param.max_iters@@
param_constraints = @@param.constraints@@

# param.constraints = {
#     'x': ('cont', [0, 1]),
#     'y': ('int', [0, 1])
# }
#
# model = GaussianProcess(matern32())
# acq = Acquisition(mode='ExpectedImprovement')
#
# model = GaussianProcessMCMC(matern32(), niter=300, burnin=100, step=pm.Slice)
# acq = Acquisition(mode='IntegratedExpectedImprovement')

model = pyGPGO.surrogates.GaussianProcess.GaussianProcess(pyGPGO.covfunc.matern32())
acq = pyGPGO.acquisition.Acquisition(mode='ExpectedImprovement')

job_id = 0
np.random.seed(param_seed)
gpgo = GPGO(model, acq, objective, param_constraints)
gpgo.run(max_iter=param_max_iters)
