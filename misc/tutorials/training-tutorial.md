
# P2Rank model training and optimization tutorial

This file provides an introduction for people who want to train and evaluate their own models or optimize different parameters of the algorithm.

## Kick-start examples

~~~sh
./prank.sh traineval -t <training_dataset> -e <evaluation_dataset>  # train and evaluate model (execute n run with different random seed, see -loop and -seed params)
./prank.sh crossval <dataset>                                       # run crossvalidation on a single dataset (see -folds param)

./prank.sh ploop -t <training_dataset> -e <evaluation_dataset> -paramA '[min:max:step]'  # iterate through param values
./prank.sh ploop -t <training_dataset>                         -paramB '(1,2,3,4)'       # iterate through param values (crossvalidation)
~~~


## Parameters

P2Rank uses global static parameters object. In the code, it can be accessed with `Params.getInst()` or through `Parametrized` trait. For full list of parameters see `Params.groovy`.

Parameters can be set in 2 ways:
1. on the command line `-<param_name> <value>`
2. in config groovy file specified with `-c <config.file>` (see working.groovy for an example... `prank -c working.groovy`). 

Parameters on the command line override those in the config file, which override defaults.

Parameter application priority (last wins):
1. default values in `Params.groovy`
2. defaults in `config/default.groovy`
3. (optionally) defaults in `config/default_rescore.groovy` only if you run `prank rescore ...`
4. `-c <config.file>`
5. command line

Note: some parameters (`-c`,`-o`, `-l/-label`) are strictly command line attributes and are not defined in `Params.groovy`. 

## Preparing the environment

Training and optimization runs can be run from the project directory (repo root) using `./prank.sh` script.

* clone P2Rank repo (https://github.com/rdk/p2rank) 
* clone dataset repo (https://github.com/rdk/p2rank-datasets) or prepare your datasets 
* copy `misc/local-env.sh` to root directory of the project and edit it according to your machine (the file is then included by `prank.sh`)
  * `cd p2rank; cp misc/local-env.sh .` 
* set available memory with `-Xmx32G` parameter. You will need a lot of memory: at least to store the whole training dataset of feature vectors and a trained model and then some (see _Raquired memory and memory/time trade-offs_) 
    
Note: for continuous work/experimentation it is better to clone the git repo, have a local java config in `local-env.sh`, and use `prank.sh` for running experiments (as described here).
The reason is that this way it will be easy to download updates (`git pull`) ot switch to a different P2Rank version (`git checkout`) while config will stay put in `local-env.sh`. 
If you decide to use downloaded `.tar.gz` distribution or `.zip` source package this will not be as easy, and you will need to manually update the config each time you download an update.

## Training and evaluation

To train a model on one dataset and evaluate its performance on the other use `prank traineval` command. 

Example:
~~~sh
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

Random Forest implementations train trees in parallel using the number of threads defined in`-rf_threads` variable.
Ideally, this would be set to the number of physical CPU cores in the machine.
However, required memory during training grows linearly with the number trees trained in parallel (`-rf_threads`), 
so you may need to lower the number of threads.

Parameters that influence memory/time trade-off:
* `-cache_datasets` determines whether datasets of proteins are kept in memory between runs**. Related parameters: 
    - `-clear_prim_caches` clear primary caches (protein structures) between runs (when iterating params or seed)
    - `-clear_sec_caches` clear secondary caches (protein surfaces etc.) between runs (when iterating params or seed)
* `-rf_threads` number of trees trained in parallel 
* `-rf_trees`, `-fr_depth` influence the size of the model in memory      
* `-rf_bagsize` influences memory needed for training and training time (defualt is `100`% but good results can be achieved with `55` or less)
* `-crossval_threads` when running crossvalidation it determines how many models are trained at the same time. Set to `1` if you don't have enough memory.

* `-cache_datasets <bool>`: keep datasets (structures and SAS points) in memory between crossval/traineval iterations. 
   For single-pass training (`-loop 1`) it does not make sense to keep it on.
   Turn off when evaluating the model on huge datasets that won't fit to memory (e.g. the whole PDB). 
   When switched off, it will leave more memory for RF at the cost of needing to parse all pdb files again.

Additional notes:
* Subsampling and supersampling influence the size of training vector dataset and required memory (see _Dealing with class imbalances_).
* Memory also grows linearly with "bag size" (`-rf_bagsize`) but this would generally be in range (50%-100%).
* Keep in mind how JVM deals with compressed OOPs. Basically it doesn't make sense to have heap size between 32G and ~48G.


### Historical note on the dataset format
(This section should be moved to historical notes as soon as there will be a new default P2Rank model.)

Parameter `-sample_negatives_from_decoys` determines how points are sampled from the proteins in a training dataset. 
If `sample_negatives_from_decoys = false` all of the points from the protein surface are used. 
If `sample_negatives_from_decoys = true` only points from decoy pockets (false-positives ligand binding sites found by other methods like Fpocket) are used. 
For that **you need to supply a training dataset that contains pocket predictions by another method** (i.e. for predictions of Fpocket use `joined-fpocket.ds` instead of `joined.ds`). 

`sample_negatives_from_decoys = true` in combination with Fpocket predictions was historically giving slightly better results. 
It focuses the classifier to learn to distinguish between true and decoy pockets which is, in theory, a harder task than to distinguish between ligandable vs. unligandable protein surface.
It also changes the ratio of sampled positives/negatives in favour of positives.

In recent P2Rank versions it might be possible to achieve better results by training from the whole protein surface in combination with class balancing techniques (see the next section).
Note that default values of other parameters (related to feature extraction and classification results aggregation) were optimized for the case where `sample_negatives_from_decoys = true`.

Here are the most relevant ones (for descriptions see `Params.groovy`):
* `-protrusion_radius` and `-neighbourhood_radius`
* `-average_feat_vectors`
* `-weight_function`
* `-pred_point_threshold`
* `-pred_min_cluster_size` 

Their values may need to be optimized again for the case when `sample_negatives_from_decoys = false`.

### Dealing with class imbalance

When using full protein surface for training (`sample_negatives_from_decoys = false`) you may need to deal with class imbalances to achieve good results.
Typically, the ratio of positives vs. negatives will be around (1:30) depending on chosen cutoffs and margins. 

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
* using different density of points on Solvent Accessible Surface for positives and negatives. It is also possible to use different density for training and evaluation.
    - `-tessellation`, `-train_tessellation`, `-train_tessellation_negatives` 
    - by default `tessellation = train_tessellation = train_tessellation_negatives = 2`
    - higher tesselation equals higher density = more datapoints
* class weight balancing
    - use `-balance_class_weights 1` in combination with `-target_class_weight_ratio`
    - works only with weight sensitive classifiers (`RandomForest`, `FastRandomForest`, `FasterForest`, `FasterForest2`)
     
Note: ratio of positives and negatives in the training dataset (as well as `-target_class_weight_ratio` if used) can strongly influence produced results.
Change in ratios will most likely need to be compensated by optimizing `-pred_point_threshold` parameter (possibly also `-point_score_pow`).
See [hyperparameter optimization tutorial](hyperparameter-optimization-tutorial.md) and [particular example](new-feature-evaluation-tutorial.md#next-we-take-a-look-how-2-parameters-influence-dca-metrics) of optimization run. 

## Crossvalidation
To run crossvalidation on a single dataset use the `prank crossval` command.

Example:
~~~sh
./prank crossval -loop 10 -seed 42 -folds 5 <dataset>    
~~~
Runs 10 independent 5-fold crossvalidation runs with different values of a random seed starting at 42. Averaged results will be written to the output directory.

Related parameters:
* `-crossval_threads <int>`: number of folds to work on simultaneously



## Output directory location

The location of the output directory for any given run is influenced by several parameters. You can organize the results of your experiments with their help.

* `-output_base_dir <dir>`: top-level default output directory
* `-out_subdir <dir>`: subdirectory of output_base_dir (optional)
* `-out_prefix_date <bool>`: add timestamp prefix to output directory name
* `-l <str>` or `-label <str>`: append a suffix to generated experiment output directory name
* `-o <dir>`: overrides previous params and places output in specified directory
                  

## Classifiers / Machine learning algorithms

P2Rank can use different ML algorithms by changing value of `-classifter` parameter (e.g. `-classifter FasterForest`). 

Random Forests implementations:
* `RandomForest`: Original implementation from Weka. Slow and memory consuming but can have marginally better predictive permormance. Uses entropy. 
* `FastRandomForest`: New faster implementation by Dan Supek. Uses entropy.
* `FasterForest`: Streamlined implementation of `FastRandomForest`. It s faster and uses leess memory, should have the same predictive preformance. Uses entropy.
* `FasterForest2`: Even faster version. Can have slightly lower predictive performnce. Uses GINI. 
     
Notes:
* `FastRandomForest` and `FasterForest` use basically the same algorithm, `FasterForest` is just more optimized.
* the differences in predictive performance are low, but the difference in consumed time and memory are high (0.5-4x).        
* (for developers) to integrate new algotithms start in `ClassifierFactory.groovy`.
       

#### Comparing training time
              
For illustration here is a comparison of training times on one particular dataset.
Value in cells is training time in minutes.     

| classifier / rf_trees | 100  | 200  | 400  | 
|-----------------------|------|------|------| 
| RandomForest          | 14.6 | 28.4 | 56.8 | 
| FastRandomForest      |  9.3 | 18.2 | 34.4 | 
| FasterForest          |  5.7 | 10.8 | 23.1 | 
| FasterForest2         |  4.4 |  8.7 | 16.0 | 

(Produced with dataset of 6.8M data points on 12 core machine.)
         

## Feature importances
     
Some classifiers can calculate feature importences during training using `-feature_importances 1` parameter.
Output will be saved to `feature_importances_sorted.csv` or `feature_importances.txt` (in case of `RandomForest`).
Training time will be impacted.

Supported by classifiers:    
* `RandomForest`: using mean impurity decrease method. Uses entropy.
* `FastRandomForest`: increase in out-of-bag error (as % misclassified instances) after feature permuted. Uses entropy.
* `FasterForest`: same as `FastRandomForest` 
* `FasterForest2`: uses new experimental method

From the experience it seems that most useful are the methods used by `FastRandomForest` and `FasterForest`.

When running more train/eval iterations (using `-loop 10` param), average importances will be calculated.
Using more trees leads to more stable results (`-rf_trees 200`).










