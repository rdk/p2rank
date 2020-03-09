# (Hyper-)parameter optimization

P2Rank has routines for optimizing arbitrary parameters with Grid and Bayesian optimization. 

Here by hyper-parameters we mean actual hyper-parameters of the machine learning models (eg. number of trees in RF) but also any arbitrary parameter ot the whole algorithm.

Comprehensive list of all parameters with descriptions is in `Params.groovy`.


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
For that you need to use `prank ploop` command and list or range expression instead of param value for one or more params. 

Supported parameter types: numerical, boolean, string and 'list of strings' (e.g feature set).

#### Defining grid
**List expression**: `(val1,val2,...)` 

Examples:
* list of numbers: `'(1,2,3,4)'`
* list of strings: `'(RandomForest,FasterForest)'`
* list of lists: `'((protrusion.bfactor.volsite),(protrusion.bfactor),(protrusion),())'`

**Range expression**: `[min:max:step]` example: `[-1:1.5:0.5]`
(Valid only for numerical parameters)

Examples:
~~~
./prank.sh ploop -t <training_dataset> -e <evaluation_dataset> -<param1> '[min:max:step]' -<param2> '(val1,val2,val3,val4)'
./prank.sh ploop -t <dataset>                                  -<param1> '[min:max:step]' -<param2> '(val1,val2,val3,val4)'   # runs crossvalidation
~~~

Random seed iteration (`-loop` and `-seed` params) works here as well.

Related parameters:
* `-clear_prim_caches <bool>`: clear primary caches (protein structures) when iterating params
* `-clear_sec_caches <bool>`: clear secondary caches (protein surfaces etc.) when iterating params

### R plots

In case you optimize exactly 1 or 2 parameters, P2Rank will try to produce plots of various statistics using R language. 
For that you need to have `Rscript` on the Path. Some libraries in R need to be installed. 
~~~
sudo apt install r-base
sudo R -e "install.packages('ggplot2', dependencies=TRUE, repos='http://cran.us.r-project.org')"
~~~

#### Real examples
    
Quick test run:
~~~   
./prank.sh ploop 
    -c working                      \      # override default config with working.groovy config file
    -t chen11-fpocket.ds            \      # crossvalidate on chen11 datsest
    -loop 1 -rf_trees 5 -rf_depth 5 \      # make it quick (1 pass, small model)
    -extra_features '((protrusion.bfactor),(protrusion.bfactor.new_feature))'` 
~~~

(Then check `run.log` in n results directory for errors. Check if R plots are generated correctly.)

Feature set comparisons:
~~~
./prank.sh ploop -c working             \      
    -t chen11-fpocket.ds                \  # crossvalidate on chen11 datsest    
    -loop 10 -rf_trees 100 -rf_depth 10 \      
    -extra_features '((protrusion.bfactor),(protrusion.bfactor.new_feature))'` 

./prank.sh ploop -c working             \      
    -t chen11-fpocket.ds                \  # train on chen11 
    -e joined.ds                        \  # and evaluate on a different dataset
    -loop 10 -rf_trees 100 -rf_depth 10 \      
    -extra_features '((protrusion.bfactor),(protrusion.bfactor.new_feature))'` 
~~~

## Bayesian optimization (hopt command)

Hopt command (`p2rank hopt`) implements Bayesian optimization using program Speramint.
(Other optimization tools might be employed in similar fashion with little additional work. 
See how integration wihth Spearmint is implemented in HSpearmintOptimizer.groovy).

Supported parameter types: numerical, boolean. 

## Install Spearmint (on ubuntu)
```sh
# Spearmint uses Python 2.7 and MongoDB       
sudo apt install -y mongodb python python-pip
sudo pip install --upgrade pip
sudo pip install numpy scipy pymongo weave
# git clone https://github.com/HIPS/Spearmint.git  # Spearmint home repo
git clone https://github.com/rdk/Spearmint.git     # fork fixing scipy.weave problem (weave-fix branch)
sudo pip install -e Spearmint
```

## Run optimization experiment

```sh
./prank.sh hopt -t <dataset>               -<param1> '(<min>,<max>)'     # crossvalidation
./prank.sh hopt -t <dataset> -e <dataset>  -<param1> '(<min>,<max>)'
```

Example:
```sh
pkill python; sudo pkill mongo; \  # prepare clean slate (careful, your other python programs might die too)
./prank.sh hopt -c working -l TREES_w -out_subdir HOPT \
    -t chen11-fpocket.ds -e joined.ds \
    -loop 1 -log_level DEBUG -log_to_console 1 \
    -ploop_delete_runs 0 \
    -hopt_spearmint_dir '/home/rdk/proj/OTHERS/Spearmint/spearmint' \
    -rf_trees '(10,200)' -rf_depth '(2,14)' -rf_features '(2,30)'   
```


