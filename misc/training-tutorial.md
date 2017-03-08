
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

### Note on the dataset format (important!)
Parameter `-sample_negatives_from_decoys` determins how points are sampled from the proteins in a training dataset. 
If `sample_negatives_from_decoys = false` all of the points from the protein surface are used. 
If `sample_negatives_from_decoys = true` only points from decoy pockets (not true ligand binding sites found by other method like Fpocket) are used. 
For that **you need to supply a training dataset that contains pocket predictions by other method** (i.e. for predictions of Fpocket use `joined-fpocket.ds` instead of `joined.ds`). 

`sample_negatives_from_decoys = true` in combination with Fpocket predictions was historically giving slightly better results. 
It focuses classifier to learn to distinguish between true and decoy pockets which is in theory harder task than to distinguish between ligandable vs. unligandable protein surface.
It also changes the ratio of sampled positives/negatives in favour of positives.

I recent versions it might be possible to achieve better results by training from whole protein surface in combination with class balancing techniques (see the next section).
Note that default values of other parameters (related to feature extraction and classification results aggregation) were optimized for the case where `sample_negatives_from_decoys = true`.

Here are the most relevant ones (for descrptions see `Params.groovy`):
* `-protrusion_radius` and `-neighbourhood_radius`
* `-average_feat_vectors`
* `-weight_function`
* `-pred_point_threshold`
* `-pred_min_cluster_size` 

Their vallues may need to be optimized again for case of `sample_negatives_from_decoys = false`.

### Dealing with class imbalances

When using all of the protein surface for traning (`sample_negatives_from_decoys = false`) you may need to deal with class imbalances to achieve good results.
Typically the ratio of positives vs. negatives will be around (1:30) depending on chosen cutoffs and margins. 

Ways to deal with class imbalances:
 
* cutoffs and margins (in relation to distance `D = <dist. to closest ligand atom>`)
    - `-positive_point_ligand_distance` points with `D < positive_point_ligand_distance` are considered positives
    - `-neutral_points_margin` if `> 0` points between `(positive_point_ligand_distance, neutral_point_margin)` are ignored  
    - `-train_lig_cutoff` if `> 0` points with `train_lig_cutoff < D` are ignored
* subsampling and supersampling
    - `-subsample`
    - `-supersample`
    - use in combination with `-target_class_ratio`
* class weight balancing
    - use `-balance_class_weights 1` in combination with `-target_class_weight_ratio`
    - works only with weight sensitive classifiers (`RandomForest`, `FastRandomForest`)


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




















