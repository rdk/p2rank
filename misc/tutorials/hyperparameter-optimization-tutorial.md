# (Hyper-)parameter optimization

P2Rank has routines for optimizing arbitrary parameters with Grid and Bayesian optimization. 

Here by hyper-parameters we mean actual hyper-parameters of the machine learning models (e.g. number of trees in RF) but also any arbitrary parameter ot the whole algorithm.

To see the complete commented list of all (including undocumented) 
parameterss see [Params.groovy](https://github.com/rdk/p2rank/blob/develop/src/main/groovy/cz/siret/prank/program/params/Params.groovy) in the source code.

**Grid optimization**: 
* generates plots for all stats 
* gives better overview of objective landscapes (and relationships of different objectives/metrics by comparing plots)  
* sensible for optimizing up to 2 parameters simultaneously

**Bayesian optimization**: 
* more efficient  
* allows to feasibly optimize multiple (6+) parameters simultaneously
* see https://doi.org/10.1109/BIBM.2017.8218024

## Grid optimization (ploop command)

P2Rank allows you to iterate experiments (train/eval and crossvalidation) through lists of different parameter values on the command line.
For that, you need to use the `prank ploop` command and list or range expression instead of param value for one or more params. 

Supported parameter types: numerical, boolean, string, and 'list of strings' (e.g. value of param `-features` has type 'list of strings').

#### Defining grid
**List expression**: `(val1,val2,...)` 

Examples:
* list of numbers: `'(1,2,3,4)'`
* list of strings: `'(RandomForest,FasterForest,FasterForest2)'`
* list of lists: `'((protrusion,bfactor,volsite),(protrusion,bfactor),(protrusion),())'`

**Range expression**: `[min:max:step]` example: `[-1:1.5:0.5]`
Valid only for numerical parameters.

Examples:
~~~sh
./prank.sh ploop -t <training_dataset> -e <evaluation_dataset> -<param1> '[min:max:step]' -<param2> '(val1,val2,val3,val4)'
./prank.sh ploop -t <dataset>                                  -<param1> '[min:max:step]' -<param2> '(val1,val2,val3,val4)'   # runs crossvalidation
~~~

Random seed iteration (`-loop` and `-seed` params) works here as well.

Related parameters:
* `-clear_prim_caches <bool>`: clear primary caches (protein structures) when iterating params
* `-clear_sec_caches <bool>`: clear secondary caches (protein surfaces etc.) when iterating params

### R plots

In case you optimize exactly 1 or 2 parameters, P2Rank will try to produce plots of various statistics using R language. 
For that, you need to have `Rscript` on the PATH. Some libraries in R need to be installed first. 
~~~sh
sudo apt install r-base
sudo R -e "install.packages(c('ggplot2','gplots','RColorBrewer'), dependencies=TRUE, repos='http://cran.us.r-project.org')"
sudo R -e "update.packages(repos='http://cran.us.r-project.org', ask = FALSE)"  # possible fix for dependency conflicts
~~~

Script to re-generate all plots
~~~sh
cd plots
find rcode | xargs -P 16 -I '{}' Rscript '{}' 
~~~

#### Real examples
    
Quick test run:
~~~sh   
./prank.sh ploop 
    -c config/train-new-default     \      # override default config with config/train-new-default.groovy config file
    -t chen11-fpocket.ds            \      # crossvalidate on chen11 datsest
    -loop 1 -rf_trees 5 -rf_depth 5 \      # make it quick (1 pass, small model)
    -features '((protrusion,bfactor),(protrusion,bfactor,new_feature))'` 
~~~

(Then check `run.log` in n results directory for errors. Check if R plots are generated correctly.)

Feature set comparisons:
~~~sh
./prank.sh ploop -c config/train-new-default \      
    -t chen11-fpocket.ds                \  # crossvalidate on chen11 dataset    
    -loop 10 -rf_trees 100 -rf_depth 10 \      
    -features '((protrusion,bfactor),(protrusion,bfactor,new_feature))'` 

./prank.sh ploop -c config/train-new-default \      
    -t chen11-fpocket.ds                \  # train on chen11 
    -e joined.ds                        \  # and evaluate on a different dataset
    -loop 10 -rf_trees 100 -rf_depth 10 \      
    -features '((protrusion,bfactor),(protrusion,bfactor,new_feature))'` 
~~~

## Bayesian optimization (hopt command)

```sh
./prank.sh hopt -t <dataset>               -<param1> '(<min>,<max>)'     # crossvalidation
./prank.sh hopt -t <dataset> -e <dataset>  -<param1> '(<min>,<max>)'
```

Hopt command (`prank hopt`) implements Bayesian optimization using one of the integrated optimizers.

Integrated optimizers (values of `-hopt_optimizer` parameter):
* `pygpgo` : __pyGPGO__  (https://github.com/josejimenezluna/pyGPGO)
* `spearmint` : __Speramint__  (https://github.com/HIPS/Spearmint.git)

(Other optimization tools might be integrated with little work. See how integration with *pyGPGO* is implemented in `HPyGpgoOptimizer.groovy`).
                             
By default, optimization goal is to maximize value of a metric in `-hopt_objective` parameter (e.g. `-hopt_objective DCA_4_0)`).
For minimization, prefix metric name with minus sign: `-hopt_objective "'-point_LOG_LOSS'"`.

Supported parameter types: `double`, `int`, `boolean`. 

## Optimization with pyGPGO

### Install pyGPGO

Requirements: Python >3.5.

```sh
pip install pyGPGO
```

## Run optimization

Examples:
```sh
./prank.sh hopt -c config/train-new-default -out_subdir HOPT -label TREES  \
    -t chen11-fpocket.ds \
    -e joined.ds \
    -hopt_optimizer 'pygpgo' \
    -hopt_python_command 'python' \
    -hopt_objective 'DCA_4_0' \
    -classifier 'FasterForest' \
    -loop 3 \
    -ploop_delete_runs 0 \
    -rf_trees '(10,200)' \
    -rf_depth '(2,14)' \
    -rf_features '(2,30)'  
    
# Optimizing parameters that are not involved in training new classifier,
# but rather in aggregating results into pockets.
# We can allow to train only one RF model in the beginning (-hopt_train_only_once 1).
# Note: this is not really ideal because of overfitting to a one particular RF model.    
./prank.sh hopt -c config/train-new-default -out_subdir HOPT -label TREES  \
    -t chen11-fpocket.ds \
    -e joined.ds \
    -hopt_optimizer 'pygpgo' \
    -hopt_python_command 'python3' \
    -hopt_objective 'DCA_4_0' \
    -classifier 'FasterForest' \
    -loop 1 \
    -hopt_train_only_once 1 \
    -pred_point_threshold '(0.2,0.6)' \
    -point_score_pow '(1,5)'
```


## Optimization with Spearmint

### Install Spearmint (on ubuntu)

Requirements: Python 2.7 and MongoDB.

```sh
sudo apt install -y mongodb python python-pip
sudo pip install --upgrade pip
sudo pip install numpy scipy pymongo weave
# git clone https://github.com/HIPS/Spearmint.git  # Spearmint home repo
git clone https://github.com/rdk/Spearmint.git     # fork fixing scipy.weave problem (weave-fix branch)
sudo pip install -e Spearmint
```

## Run optimization 

Example:
```sh
pkill python; sudo pkill mongo;   # prepare clean slate (careful, your other python programs might die too)

./prank.sh hopt -c config/train-new-default -out_subdir HOPT -label TREES  \
    -t chen11-fpocket.ds \
    -e joined.ds \
    -hopt_optimizer 'spearmint' \
    -hopt_python_command 'python' \
    -hopt_spearmint_dir '../Spearmint/spearmint' \
    -hopt_objective 'DCA_4_0' \
    -classifier 'FasterForest' \
    -loop 3 \
    -ploop_delete_runs 0 \
    -rf_trees '(10,200)' \
    -rf_depth '(2,14)' \
    -rf_features '(2,30)'   
```


