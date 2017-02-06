
# P2RANK model traning and optimization turorial

This file should provide interduction for people who want to train and evaluate their own models or optimize different parameters of the algorithm.

## Kick-start examples

~~~
prank traineval -t <training_dataset> -e <evaluation_dataset>  # train and evaluate model (execute n run with difefrent random seed, see -loop and -seed params)
prank crossval <dataset>                                       # run crossvalidation on a single dataset (see -folds param)

prank ploop -t <training_dataset> -e <evaluation_dataset> -paramA '[min:max:step]'  # iterate through param values
prank ploop -t <training_dataset>                         -paramB '(1,2,3,4)'       # iterate through param values (crossvalidation)
~~~


## Parameters

P2RANK uses global static parameters object. In code it can be accessed with `Params.getInst()` or through `Parametrized` trait. For full list of parameters see `Params.groovy`.

Parameters can be set in 2 ways:
1. on the command line `-<param_name> <value>`
2. in config groovy file specified with `-c <config.file>` (see working.groovy for an example... `prank -c working.groovy`). 

Parameters on the command line override those in the config file, which override defaults.

Parameter application priority (last wins):
1. default values in `Params.groovy`
2. defaults in `config/default.groovy`
3. (optionally) defaults in `config/default-rescore.groovy` only if you run `prank rescore ...`
4. `-c <config.file>`
5. command line

Note: some parameters (`-c`,`-o`, `-l/-label`) are sctrictly command line attributes and are not defined in `Params.groovy`. 


## Training and evaluation

To train a model on one dataset and evaluate its performance on the other use `prank traineval` command. 

Example:
~~~
prank traineval -loop 10 -seed 42 -t <training_dataset> -e <evaluation_dataset>`
~~~
Runs 10 training/evaluation cycles with different values of a random seed starting at 42. 
Results of any single train/eval run and averaged results will be written to the output directory.

Related parameters:
* use `-delete_models 0` to keep model files after evaluation.
* `-cache_datasets <bool>`: keep datasets (structures and Connolly points) in memory between crossval/traineval iterations. Turn off for huge datasets that won't fit to memory.
* `-feature_importances <bool>`: calculate feature importances (works only if `classifier = "FastRandomForest"`)
* `-fail_fast <bool>`: stop processing the datsaset on the first unrecoverable error with a dataset item

### Note on dataset format
Parameter `-train_all_surface` determins how are points sampled from proteins in training dataset. 
If `train_all_surface = true` all all of the points from the protein surface are used. 
If `train_all_surface = false` only points from decoy pockets (not true ligand binding sites found by other method like Fpocket) are used. 
For that you need to suply dataset that contains Fpocket predictions (i.e. `joined-fpocket.ds` instead of `joined.ds`). 

`train_all_surface = false` ususlly gives slightly better results. When using `train_all_surface = true` you may need to play with subsampling. 

## Crossvalidation
To run crossvalidation on a single dataset use `prank crossval` command.

Example:
~~~
prank crossval -loop 10 -seed 42 -folds 5 <dataset>    
~~~
Runs 10 independent 5-fold crossvalidation runs with different values of a random seed starting at 42. Averaged results will be written to the output directory.

Related parameters:
* `-crossval_threads <int>`: number of folds to work on simultaneously

    

## Grid optimization

P2RANK allows you to iterate exeriments (train/eval and crossvalidation) through lists of different parameter values on the command line.

For that you need to use `prank ploop` command and list or range experssion instead of param value for one or more params. Only numerical and boolean parameters are suppeotrd.


List expression: `(val1,val2,...)` example: `'(1,2,3,4)'`

Range expression: `[min:max:step]` example: `[-1:1.5:0.5]`

Examples:
~~~
prank ploop -t <training_dataset> -e <evaluation_dataset> -paramA '[min:max:step]' -paramB '(val1,val2,val3,val4)'
prank ploop -t <dataset>                                  -paramA '[min:max:step]' -paramB '(val1,val2,val3,val4)'   # runs crossvalidation
~~~

Random seed iteration (`-loop` and `-seed` params) works here as well.

Related parameters:
* `-clear_prim_caches <bool>`: clear primary caches (protein structures) when iterating params
* `-clear_sec_caches <bool>`: clear secondary caches (protein surfaces etc.) when iterating params

### R plots

In case you iterate through exactly 1 or 2 parameters P2RANK will try to procuce plots of various statistics using R language. For thet you need to have `Rscript` on the Path. Some libraries in R need to be installed. 


## Output directory location

Location of output directory for any given run is influenced by several paramaters. You can organize results of your experimants with their help.

* `-output_base_dir <dir>`: top level default output directory
* `-out_subdir <dir>`: subdirectory of output_base_dir (optional)
* `-out_prefix_date <bool>`: prefix generated experiment output directory name with a timestamp
* `-l <str>` or `-label <str>`: define suffix to generated experiment output directory name
* `-o <dir>`: overrides previous params and places output in specified directory




















