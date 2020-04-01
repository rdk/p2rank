
# P2Rank model training and optimization tutorial

This file should provide introduction for people who want to train and evaluate their own models or optimize different parameters of the algorithm.

## Kick-start examples

~~~
./prank.sh traineval -t <training_dataset> -e <evaluation_dataset>  # train and evaluate model (execute n run with difefrent random seed, see -loop and -seed params)
./prank.sh crossval <dataset>                                       # run crossvalidation on a single dataset (see -folds param)

./prank.sh ploop -t <training_dataset> -e <evaluation_dataset> -paramA '[min:max:step]'  # iterate through param values
./prank.sh ploop -t <training_dataset>                         -paramB '(1,2,3,4)'       # iterate through param values (crossvalidation)
~~~


## Parameters

P2Rank uses global static parameters object. In code it can be accessed with `Params.getInst()` or through `Parametrized` trait. For full list of parameters see `Params.groovy`.

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

Note: some parameters (`-c`,`-o`, `-l/-label`) are strictly command line attributes and are not defined in `Params.groovy`. 

## Preparing the environment

Training and optimization runs can be run from the project directory (repo root) using `./prank.sh` wrapper.

* clone P2Rank repo (https://github.com/rdk/p2rank) 
* clone dataset repo (https://github.com/rdk/p2rank-datasets) or prepare your datasets 
* copy `misc/local-env.sh` to root directory of the project and edit it according to your machine (the file is then included by `prank.sh`)
* you will need a lot of memory: at least to store the whole training dataset of feature vectors and a trained model and then some (see _Raquired memory and memory/time trade-offs_) 


## Training and evaluation

To train a model on one dataset and evaluate its performance on the other use `prank traineval` command. 

Example:
~~~
./prank.sh traineval -loop 10 -seed 42 -t <training_dataset> -e <evaluation_dataset>`
~~~
Runs 10 training/evaluation cycles with different values of a random seed starting at 42. 
Results of any single train/eval run and averaged results will be written to the output directory.

Related parameters:
* use `-delete_models 0` to keep model files after evaluation.
* use `-delete_vactors 0` to export feature vector files 
* `-feature_importances <bool>`: calculate feature importances (works only if `-classifier` supports it, examples: `RandomForest`, `FastRandomForest`, `FasterForest`)
* `-fail_fast <bool>`: stop processing the dataset on the first unrecoverable error with a dataset item

### Raquired memory and memory/time trade-offs

Memory consumption can be drastically influenced by some parameters.

Random Forest implementations train trees in parallell using number of threads defined in`-rf_threads` variable.
Ideally, this would be set to number of CPU cores in the machine.
However, required memory during training grows linearly with number trees trained in paralell (`-rf_threads`) 
so you mey need to lower number of threads.

Parameters that influence memory/time trade-off:
* `-cache_datasets` determines whether datasets of proteins are kept in memory between runs**. Related parameters: 
    - `-clear_prim_caches` clear primary caches (protein structures) between runs (when iterating params or seed)
    - `-clear_sec_caches` clear secondary caches (protein surfaces etc.) between runs (when iterating params or seed)
* `-rf_threads` number of trees trained in parallell 
* `-rf_trees`, `-fr_depth` influence the size of the model in memory      
* `-crossval_threads` when running crossvalidation it determines how many models are trained at the same time. Set to `1` if you don't have enough memory.

** `-cache_datasets <bool>`: keep datasets (structures and SAS points) in memory between crossval/traineval iterations. 
   For single pass training (`-loop 1`) it does not make sense to keep it on.
   Turn off when evaluating model on huge datasets that won't fit to memory (e.g. whole PDB). 
   When switched off it will leave more memory for RF at the cost of needing to parse all structure files (PDBs) again.

Additional notes:
* Subsampling and supersampling influence the size of training vercor dataset and required memory (see _Dealing with class imbalances_).
* Memory also grows linearly with "bag size" (`-rf_bagsize`) but this would generally be in range (50%-100%).
* Keep in mind how JVM deals with compressed OOPs. Basically it doesn't make sense to have heap size between 32G and ~48G.


### Historical note on the dataset format
(This secton should be moved no historical notes as soon as there will be new default P2Rank model.)

Parameter `-sample_negatives_from_decoys` determines how points are sampled from the proteins in a training dataset. 
If `sample_negatives_from_decoys = false` all of the points from the protein surface are used. 
If `sample_negatives_from_decoys = true` only points from decoy pockets (not true ligand binding sites found by other method like Fpocket) are used. 
For that **you need to supply a training dataset that contains pocket predictions by other method** (i.e. for predictions of Fpocket use `joined-fpocket.ds` instead of `joined.ds`). 

`sample_negatives_from_decoys = true` in combination with Fpocket predictions was historically giving slightly better results. 
It focuses classifier to learn to distinguish between true and decoy pockets which is in theory harder task than to distinguish between ligandable vs. unligandable protein surface.
It also changes the ratio of sampled positives/negatives in favour of positives.

I recent versions it might be possible to achieve better results by training from whole protein surface in combination with class balancing techniques (see the next section).
Note that default values of other parameters (related to feature extraction and classification results aggregation) were optimized for the case where `sample_negatives_from_decoys = true`.

Here are the most relevant ones (for descriptions see `Params.groovy`):
* `-protrusion_radius` and `-neighbourhood_radius`
* `-average_feat_vectors`
* `-weight_function`
* `-pred_point_threshold`
* `-pred_min_cluster_size` 

Their values may need to be optimized again for case of `sample_negatives_from_decoys = false`.

### Dealing with class imbalance

When using all of the protein surface for training (`sample_negatives_from_decoys = false`) you may need to deal with class imbalances to achieve good results.
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
    - can sbstantially influence the size of the training vactor dataset and consequentially the required memory
* class weight balancing
    - use `-balance_class_weights 1` in combination with `-target_class_weight_ratio`
    - works only with weight sensitive classifiers (`RandomForest`, `FastRandomForest`, `FasterForest`, `FasterForest2`)


## Crossvalidation
To run crossvalidation on a single dataset use `prank crossval` command.

Example:
~~~
./prank crossval -loop 10 -seed 42 -folds 5 <dataset>    
~~~
Runs 10 independent 5-fold crossvalidation runs with different values of a random seed starting at 42. Averaged results will be written to the output directory.

Related parameters:
* `-crossval_threads <int>`: number of folds to work on simultaneously



## Output directory location

Location of output directory for any given run is influenced by several parameters. You can organize results of your experiments with their help.

* `-output_base_dir <dir>`: top level default output directory
* `-out_subdir <dir>`: subdirectory of output_base_dir (optional)
* `-out_prefix_date <bool>`: prefix generated experiment output directory name with a timestamp
* `-l <str>` or `-label <str>`: define suffix to generated experiment output directory name
* `-o <dir>`: overrides previous params and places output in specified directory



    











