package cz.siret.prank.program.params

import com.google.common.annotations.Beta
import com.google.common.collect.ImmutableSet
import cz.siret.prank.program.Main
import cz.siret.prank.program.PrankException
import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.Sutils
import groovy.transform.AutoClone
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Holds all global parameters of the program.
 *
 * This file is also main source of parameter description/documentation.
 *
 * Parameter annotations:
 * @RuntimeParam            ... Parameters related to program execution.
 * @ModelParam              ... Actual parameters of the algorithm, related to extracting features and calculating results.
 *                              It is important that those parameters stay the same when training a model and then using it for inference.
 * @ModelParam // training  ... Model params used only in training phase but not during inference.
 */
@Slf4j
@AutoClone
@CompileStatic
class Params {

    public static Params INSTANCE = new Params()

    public static Params getInst() {
        return INSTANCE
    }

    /**
     * define this if you want dataset program parameters to be evaluated relative to this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    @RuntimeParam
    String dataset_base_dir = null

    /**
     * all output of the program will be stored in subdirectories of this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    @RuntimeParam
    String output_base_dir = null

    /**
     * Location of pre-trained serialized model.
     * (set path relative to install_dir/models/)
     */
    @RuntimeParam
    String model = "default.model"

    /**
     * Random seed
     */
    @RuntimeParam
    int seed = 42

    /**
     * Parallel execution (processing datasets in parallel)
     */
    @RuntimeParam
    boolean parallel = true

    /**
     * Number of computing threads
     */
    @RuntimeParam
    int threads = Runtime.getRuntime().availableProcessors() + 1

    /**
     * Number for threads used for generating R plots
     */
    @RuntimeParam
    int r_threads = 2

    /**
     * Generate plots for each metric with R when doing grid optimization (ploop command) on 1 or 2 variables
     */
    @RuntimeParam
    boolean r_generate_plots = true

    /**
     * Generate standard deviation plot for each statistic when generating R plots
     */
    @RuntimeParam
    boolean r_plot_stddevs = false

    /**
     * Number of cross-validation folds to work on simultaneously.
     * (Multiplies required memory)
     */
    @RuntimeParam
    int crossval_threads = 1

    /**
     * defines witch atoms around the ligand are considered to be part of the pocket
     * (ligands with longer distance are considered 'distant', i.e. irrelevant floating ligands)
     */
    @ModelParam // training
    double ligand_protein_contact_distance = 4

    /**
     * acceptable distance between ligand center and closest protein atom for relevant ligands
     * (ligands with longer distance are considered 'distant', i.e. irrelevant floating ligands)
     */
    @ModelParam // training
    double ligc_prot_dist = 5.5

    //==[ Features ]=========================================================================================================//

    /**
     * List of general calculated features
     */
    @ModelParam
    List<String> features = ["chem", "protrusion", "bfactor", "atom_table", "residue_table"]

    /**
     * List that is added to the features list on runtime.
     * Useful in grid optimization mode for comparing different new features.
     */
    @ModelParam
    List<String> extra_features = []

    /**
     * List of features that come directly from atom type tables
     * see atomic-properties.csv
     */
    @ModelParam
    List<String> atom_table_features = ["apRawValids","apRawInvalids","atomicHydrophobicity"]

    /**
     * List of features that come directly from residue tables
     */
    @ModelParam
    List<String> residue_table_features = []

    /**
     * List of feature filters that are applied to individual features (i.e. sub-features).
     * If empty all individual features are used.
     * Filters are applied sequentially.
     *
     * Examples of individual filters:
     * <ul>
     *   <li> "*" - include all
     *   <li> "chem.*" - include all with prefix "chem."
     *   <li> "-chem.*" - exclude all with prefix "chem."
     *   <li> "chem.hydrophobicity" - include particular sub-feature
     *   <li> "-chem.hydrophobicity" - exclude particular sub-feature
     * </ul>
     *
     * If the first filter in feature_filters starts with "-", include-all filter ("*") is implicitly applied to the front.
     *
     * Examples of full feature_filters values:
     * <ul>
     *   <li> [] - include all
     *   <li> ["*"] - include all
     *   <li> ["*","-chem.*"] - include all except those with prefix "chem."
     *   <li> ["-chem.*"] - include all except those with prefix "chem."
     *   <li> ["-chem.*","chem.hydrophobicity"] - include all except those with prefix "chem.", but include "chem.hydrophobicity"
     *   <li> ["chem.hydrophobicity"] - include only "chem.hydrophobicity"
     *   <li> ["chem.*","-chem.hydrophobicity","-chem.atoms"] - include only those with prefix "chem.", except "chem.hydrophobicity" and "chem.atoms"
     * </ul>
     */
    @ModelParam
    List<String> feature_filters = []

    /**
     * Exponent applied to all atom table features // TODO change default to 1
     */
    @ModelParam
    double atom_table_feat_pow = 2

    /**
     * Dummy param to preserve behaviour of older versions.
     * Should be set to true for training new models.
     *
     * If true sign of value is reapplied after transformation by atom_table_feat_pow
     */
    @ModelParam
    boolean atom_table_feat_keep_sgn = false

    /**
     * radius for calculation protrusion feature
     */
    @ModelParam
    double protrusion_radius = 10

//===========================================================================================================//

    /**
     * Number of bins for protr_hist feature, must be >=2
     */
    @ModelParam
    int protr_hist_bins = 5

    /**
     * Param of protr_hist feature
     */
    @ModelParam
    boolean protr_hist_cumulative = false

    /**
     * Param of protr_hist feature
     */
    @ModelParam
    boolean protr_hist_relative = false

    /**
     * Number of bins for Atom Pair distance histogram (pair_hist) feature, must be >=2
     */
    @ModelParam
    int pair_hist_bins = 5

    /**
     * Radius capturing atoms considered in pair_hist feature
     */
    @ModelParam
    double pair_hist_radius = 6

    /**
     * smooth vs. sharp binning
     */
    @ModelParam
    boolean pair_hist_smooth = false

    /**
     * apply normalization to histogram
     */
    @ModelParam
    boolean pair_hist_normalize = false

    /**
     * if false only protein exposed atoms are considered
     */
    @ModelParam
    boolean pair_hist_deep = true

    /**
     * size of random subsample of atom pairs, 0 = all
     */
    @ModelParam
    int pair_hist_subsample_limit = 0

//===========================================================================================================//

    /**
     * Load sequence conservation data
     */
    @RuntimeParam
    boolean load_conservation = false


    /**
     * Pocket scoring algorithm
     */
    @ModelParam
    String score_pockets_by = "p2rank" // possible values: "p2rank", "conservation", "combi"

    /**
     * Conservation exponent for re-scoring pockets
     */
    @ModelParam
    int conservation_exponent = 1

    /**
     * Radius for calculating conservation cloud related features
     */
    @ModelParam
    double conserv_cloud_radius = 10

    /**
     * Radius for calculating secondary structure cloud related features
     */
    @ModelParam
    double ss_cloud_radius = 10

    /**
     * Directories in which to look for conservation score files.
     * Path is absolute or relative to the dataset directory.
     * If null or empty: look in the same directory as protein file
     */
    @RuntimeParam
    List<String> conservation_dirs = []

    @RuntimeParam
    List<String> electrostatics_dirs = []


    /**
     * Log scores for binding and nonbinding scores to file
     */
    @RuntimeParam
    String log_scores_to_file = ""

    /**
     * limits how many pocket SAS points are used for scoring (after sorting), 0=unlimited
     * affects scoring pockets and also residues
     */
    @ModelParam
    int score_point_limit = 0

//==[ Classifiers ]=========================================================================================================//

    /**
     * see ClassifierOption
     */
    @ModelParam
    String classifier = "FastRandomForest"

    /**
     * see ClassifierOption
     */
    @ModelParam
    String inner_classifier = "FastRandomForest"

    /**
     * see ClassifierOption
     */
    @ModelParam
    int meta_classifier_iterations = 5

    /**
     * works only with classifier "CostSensitive_RF"
     */
    @ModelParam // training
    double false_positive_cost = 2

    //=== Random Forests =================

    /**
     * RandomForest trees
     */
    @ModelParam // training
    int rf_trees = 100

    /**
     * RandomForest depth limit, 0=unlimited
     */
    @ModelParam // training
    int rf_depth = 0

    /**
     * RandomForest feature subset size for one tree, 0=default(sqrt)
     */
    @ModelParam // training
    int rf_features = 0

    /**
     * number of threads used in RandomForest training (0=use value of threads param)
     */
    @RuntimeParam // training
    int rf_threads = 0

    /**
     * size of a bag: 1..100% of the dataset
     */
    @ModelParam // training
    int rf_bagsize = 100

    /**
     * Flatten random forest if possible
     */
    @RuntimeParam
    @ModelParam // training
    boolean rf_flatten = false

    /**
     * Flatten random forest in a way that has exactly the same output
     * by preserving weird way tree results are aggregated in FastRandomForest.
     */
    @RuntimeParam
    @ModelParam // training
    boolean rf_flatten_as_legacy = true

    /**
     * cutoff for joining ligand atom groups into one ligand
     */
    @ModelParam // training
    double ligand_clustering_distance = 1.7 // ~= covalent bond length

    /**
     * cutoff around ligand that defines positives
     */
    @ModelParam
    double positive_point_ligand_distance = 2.5

    /**
     * distance around ligand atoms that define ligand induced volume
     * (for evaluation by some criteria, DSO, ligand coverage...)
     */
    @ModelParam
    double ligand_induced_volume_cutoff = 2.5

    /**
     * points between (positive_point_ligand_distance, positive_point_ligand_distance + neutral_point_margin) will not be considered positives or negatives and will be left out form training
     */
    @ModelParam // training
    double neutral_points_margin = 5.5

    /**
     * Neighbourhood radius (A) used for calculating most of the features.
     */
    @ModelParam
    double neighbourhood_radius = 8

    /**
     * HETATM groups that are ignored (not marked as relevant ligands, e.g because they are cofactors or part of a substrate)
     */
    @ModelParam // training
    List<String> ignore_het_groups = ["HOH","DOD","WAT","NAG","MAN","UNK","GLC","ABA","MPD","GOL","SO4","PO4"]

    /**
     * Which ligand types define positive SAS points.
     * accepted values: "relevant", "ignored", "small", "distant"
     */
    @ModelParam // training
    List<String> positive_def_ligtypes = ["relevant"]

    /**
     * Minimal heavy atom count for relevant ligands, other ligands are considered too small and ignored
     */
    @ModelParam // training
    int min_ligand_atoms = 5

    /**
     * Point sampler for extracting instances for training.
     * P2Rank and PRANK use SurfacePointSampler that produces SAS points.
     * Others like GridPointSampler are experimental, and also deprecated. see point_sampling_strategy
     */
    @ModelParam
    String point_sampler = "SurfacePointSampler"

    /**
     * surface | atoms | grid
     */
    @Beta
    String point_sampling_strategy = "surface"

    /**
     * multiplier for random point sampling
     */
    @ModelParam // training
    int sampling_multiplier = 3

    /**
     * solvent radius for SAS surface
     */
    @ModelParam
    double solvent_radius = 1.6

    /**
     * SAS tessellation (~density) used in prediction step.
     * Higher tessellation = higher density (+1 ~~ x4 points)
     */
    @ModelParam
    int tessellation = 2

    /**
     * SAS tessellation (~density) used in training step
     * 0 = use value of tessellation
     */
    @ModelParam // training
    int train_tessellation = 2

    /**
     * SAS tessellation (~density) used in training step to select negatives.
     * Allows denser positive sampling than negative sampling and thus deal with class imbalance and train faster.
     * 0 = use value of effective train_tessellation
     */
    @ModelParam // training
    int train_tessellation_negatives = 2

    /**
     * for grid and random sampling
     */
    @ModelParam
    double point_min_distfrom_protein = 2.5

    /**
     * for grid and random sampling
     */
    @ModelParam
    double point_max_distfrom_pocket = 4.5

    /**
     * grid cell size for grid sampling strategy (and old GridPointSampler)
     */
    @ModelParam
    double grid_cell_edge = 2

    /**
     * Cutoff radius around protein atoms. Grid points with higher distance to closest protein atom are discarded.
     */
    @ModelParam
    double grid_cutoff_radius = 3.4

    /**
     * Restrict training set size, 0=unlimited
     */
    @RuntimeParam // training
    int max_train_instances = 0

    /**
     * Param of SAS score weighting function (see WeightFun)
     */
    @ModelParam
    double weight_power = 2

    /**
     * Param of SAS score weighting function (see WeightFun)
     */
    @ModelParam
    double weight_sigma = 2.2

    /**
     * Param of SAS score weighting function (see WeightFun)
     */
    @ModelParam
    double weight_dist_param = 4.5

    /**
     * Choice of SAS score weighting function (see WeightFun)
     */
    @ModelParam
    String weight_function = "INV"

    /**
     * If false only single layer of proteins solvent exposed atoms is used for calculating features that are projected from protein atoms to SAS points
     */
    @ModelParam
    boolean deep_surrounding = false

    /** calculate feature vectors from smooth atom feature representation
     * (instead of directly from atom properties)
     */
    @Deprecated
    @ModelParam
    boolean smooth_representation = false

    /**
     * related to smooth_representation
     */
    @Deprecated
    @ModelParam
    double smoothing_radius = 4.5

    /**
     * determines how atom feature vectors are projected on to SAS point feature vector
     * if true, atom feature vectors are averaged
     * else they are only summed up
     */
    @ModelParam
    boolean average_feat_vectors = false

    /**
     * in feature projection from atoms to SAS points:
     * only applicable when average_feat_vectors=true
     * <0,1> goes from 'no average, just sum' -> 'full average'
     */
    @ModelParam
    double avg_pow = 1

    /**
     * regarding feature projection from atoms to SAS points: calculate weighted average
     * (should be true by default, kept false for backward compatibility reasons)
     */
    @ModelParam
    boolean avg_weighted = false

    /**
     * exponent of point ligandability score (before adding it to pocket score)
     */
    @ModelParam
    double point_score_pow = 2

    /**
     * exponent of point ligandability score (before adding it to residue score in residue prediction mode)
     * value less than 0 refers to the value of point_score_pow
     */
    @ModelParam
    double residue_point_score_pow = -1

    /**
     * Binary classifiers produces histogram of scores for class0 and class1
     * if true only score for class1 is considered
     * makes a difference only if histogram produced by classifier doesn't sum up to 1
     */
    @ModelParam
    boolean use_only_positive_score = true

    /**
     * If true trained models will not be saved to disk (good for parameter optimization)
     */
    @RuntimeParam
    boolean delete_models = false

    /**
     * delete files containing training/evaluation feature vectors
     */
    @RuntimeParam
    boolean delete_vectors = true

    /**
     * check all loaded/calculated vectors for invalid (NaN) values
     */
    @RuntimeParam
    boolean check_vectors = false

    /**
     * collect vectors also from eval dataset (only makes sense in combination with delete_vectors=false)
     */
    @RuntimeParam
    boolean collect_eval_vectors = false

    /**
     * collect vectors only at the beginning of seed loop routine
     * if dataset is sub-sampled (using train_protein_limit param) then dataset is sub-sampled only once
     * set to false when calculating learning curve!
     * train_protein_limit>0 should be always paired with collect_only_once=false
     */
    @RuntimeParam
    boolean collect_only_once = true

    /**
     * number of random seed iterations
     *
     * Only relevant when training and evaluating new models.
     * Result metrics are then averaged or calculated for sum of runs (where appropriate, like F1 measure).
     * Example: using running  traineval with loop=10 will do ten runs with different random seed and calculate averages.
     */
    @RuntimeParam
    int loop = 1

    /**
     * keep datasets (structures and SAS points) in memory between crossval/seedloop iterations
     */
    @RuntimeParam
    boolean cache_datasets = false


    /**
     * calculate feature importances
     * available only for some classifiers
     */
    @RuntimeParam
    boolean feature_importances = false

    /**
     * produce pymol visualisations
     */
    @RuntimeParam
    boolean visualizations = true

    /**
     * visualize all surface points (not just inner pocket points)
     */
    @RuntimeParam
    boolean vis_all_surface = false

    /**
     * copy all protein pdb files to visualization folder (making visualizations portable)
     */
    @RuntimeParam
    boolean vis_copy_proteins = true

    /**
     * generate new protein pdb files from structures in memory instead of reusing input files
     * (useful when structures were manipulated in memory, e.g. when reducing to specified chains)
     */
    @RuntimeParam
    boolean vis_generate_proteins = true

    /**
     * Highlight ligands by rendering them as enlarged balls (instead of sticks).
     * Necessary to see 1 atom ligands like ions.
     * Affects rendering only in pocket mode.
     */
    @RuntimeParam
    boolean vis_highlight_ligands = false

    /**
     * zip PyMol visualizations to save space
     */
    @RuntimeParam
    boolean zip_visualizations = false

    /**
     * use strictly inner pocket points or more wider pocket neighbourhood
     */
    @RuntimeParam
    boolean strict_inner_points = false

    /**
     * cross-validation folds
     */
    @RuntimeParam
    int folds = 5

    /**
     * collect evaluations for top [n+0, n+1,...] pockets (n is true pocket count)
     */
    @RuntimeParam
    List<Integer> eval_tolerances = [0,1,2,4,10,99]

    /**
     * Calculate pocket predictions.
     * This is a main switch between re-scoring of predictions by other methods (PRANK) and pocket prediction (P2Rank)
     */
    @RuntimeParam
    boolean predictions = true

    /**
     * Residue prediction mode (as opposed to full pocket prediction mode)
     */
    @RuntimeParam
    boolean predict_residues = false

    /**
     * If true, assign class to SAS points in training dataset based on proximity to the ligand.
     * If false, assign class based the class of nearest residue.
     * Distinction only makes sense running in residue prediction mode (predict_residues = true).
     */
    @RuntimeParam
    boolean ligand_derived_point_labeling = true

    /**
     * produce residue labeling file (in predict mode)
     *
     * Even in full pocket prediction mode (predict_residues=false) we can label and score residues using transformers.
     */
    @RuntimeParam
    boolean label_residues = true

    /**
     * residue score threshold for calculating predicted binary label
     */
    @ModelParam
    double residue_score_threshold = 1d

    /**
     * in calculation of residue score from neighboring SAS points:
     * <0,1> goes from 'no average, just sum' -> 'full average'
     */
    @ModelParam
    double residue_score_sum_to_avg = 0d

    /**
     * added to the cutoff distance around residue in score aggregation from SAS points
     * full distance cutoff R around residue atoms is calculated as follows:
     * R = solvent_radius + surface_additional_cutoff + residue_score_extra_dist
     */
    @ModelParam
    double residue_score_extra_dist = 0d

    /**
     * Calculate residue scores only for exposed residues (inner will have score 0)
     * => only exposed residues can be predicted as positive.
     * Makes sense only in combination with point_sampling_strategy=surface.
     */
    @ModelParam
    boolean residue_score_only_exposed = false

    /**
     * residue score transform function
     * 
     * NONE: identity .. score will be in range <0,inf)
     * SIGMOID: score will be transformed to range <0,1)
     */
    @ModelParam
    String residue_score_transform = "NONE"

    /**
     * minimum ligandability score for SAS point to be considered ligandable
     */
    @ModelParam
    double pred_point_threshold = 0.4

    /**
     * minimum cluster size (of ligandable points) for initial clustering
     */
    @ModelParam
    int pred_min_cluster_size = 3

    /**
     * clustering distance for ligandable clusters for second phase clustering
     */
    @ModelParam
    double pred_clustering_dist = 5

    /**
     * SAS points around ligandable points (an their score) will be included in the pocket
     */
    @ModelParam
    double extended_pocket_cutoff = 3.5

    /**
     * cutoff distance of protein surface atoms considered as part of the pocket
     */
    @ModelParam
    double pred_protein_surface_cutoff = 3.5

    /**
     * Prefix output directory with date and time
     */
    @RuntimeParam
    boolean out_prefix_date = false

    /**
     * Place all output files in this sub-directory of the output directory
     */
    @RuntimeParam
    String out_subdir = null

    /**
     * Balance SAS point score weight by density (points in denser areas will have lower weight)
     */
    @ModelParam
    boolean balance_density = false

    /**
     * Radius for balancing of SAS point score weight
     */
    @ModelParam
    double balance_density_radius = 2

    /**
     * output detailed tables for all proteins, ligands and pockets or residues
     */
    @RuntimeParam
    boolean log_cases = true

    /**
     * cutoff for protein exposed atoms calculation (distance from SAS surface is solv.radius. + surf_cutoff)
     */
    @ModelParam
    double surface_additional_cutoff = 1.8

    /**
     * collect negatives just from decoy pockets found by other method
     * (alternatively take negative points from all of the protein's surface)
     */
    @ModelParam // training
    boolean sample_negatives_from_decoys = false

    /**
     * cutoff around ligand atoms to select negatives, 0=all
     * valid if training from whole surface (sample_negatives_from_decoys=false)
     */
    @ModelParam // training
    double train_lig_cutoff = 0

    /**
     * n, use only top-n pockets to select training instances, 0=all
     */
    @ModelParam // training
    int train_pockets = 0

    /**
     * clear primary caches (protein structures) between runs (when iterating params or seed)
     */
    @RuntimeParam // training
    boolean clear_prim_caches = false

    /**
     * clear secondary caches (protein surfaces etc.) between runs (when iterating params or seed)
     */
    @RuntimeParam // training
    boolean clear_sec_caches = false

    /**
     * Select pocket re-scoring algorithm when running in re-scoring mode (predictions=false).
     *
     * Published PRANK (2015) = "ModelBasedRescorer"
     */
    @ModelParam
    String rescorer = "ModelBasedRescorer"

    /**
     * Parameter of the PLBIndexRescorer algorithm.
     */
    @ModelParam
    boolean plb_rescorer_atomic = false

    /**
     * stop processing the dataset on the first unrecoverable error with a dataset item
     */
    @RuntimeParam
    boolean fail_fast = false

    /**
     * Fail when (X-masked) sequences in the structure and in the conservation score file do not match exactly.
     * Has effect only when fail_fast = true.
     */
    @RuntimeParam
    boolean fail_on_conserv_seq_mismatch = false

    /**
     * target class ratio of positives/negatives we train on.
     * relates to subsampling and supersampling
     */
    @RuntimeParam // training
    double target_class_ratio = 0.1

    /**
     * in training use subsampling to deal with class imbalance
     */
    @RuntimeParam // training
    boolean subsample = false

    /**
     * in training use supersampling to deal with class imbalance
     */
    @RuntimeParam // training
    boolean supersample = false

    /**
     * sort negatives desc by protrusion before subsampling
     */
    @RuntimeParam // training
    boolean subsampl_high_protrusion_negatives = false

    /**
     * don't produce prediction files for individual proteins (useful for long repetitive experiments)
     */
    @RuntimeParam 
    boolean output_only_stats = false

    /**
     * compress results of individual ploop runs
     */
    @RuntimeParam
    boolean ploop_zip_runs = false

    /**
     * delete results of individual ploop/hopt runs
     */
    @RuntimeParam
    boolean ploop_delete_runs = false

    /**
     * logging level (TRACE/DEBUG/INFO/WARN/ERROR)
     */
    @RuntimeParam
    String log_level = "INFO"

    /**
     * print log messages to console
     */
    @RuntimeParam
    boolean log_to_console = true

    /**
     * print log messages to file (run.log in outdir)
     */
    @RuntimeParam
    boolean log_to_file = true

    /**
     * Timestamp that will be added as a prefix to each message printed to stdout ("" = no timestamp)
     * Example: "yyyy.MM.dd HH:mm:"
     */
    @RuntimeParam
    String stdout_timestamp = ""


    /**
     * compress and delete log file at the end (if log_to_file)
     */
    @RuntimeParam
    boolean zip_log_file = false

    /**
     * limit the number of proteins that used for training. random subset of proteins from the dataset is used each run in seedloop
     * 0 = no limit
     */
    @RuntimeParam // training
    int train_protein_limit = 0

    /**
     * add weights to instances to achieve target_weight_ratio (if classifier algorithm supports it)
     *
     */
    @ModelParam // training
    boolean balance_class_weights = false

    /**
     * target ratio of weighted sums of positive/negative instances when balancing class weights (balance_class_weights=true)
     */
    @ModelParam // training
    double target_class_weight_ratio = 0.1

    /**
     * produce classifier stats also for train dataset
     */
    @RuntimeParam // training
    boolean classifier_train_stats = false

    /**
     * Collect predictions for all points in the dataset.
     * Allows calculation of AUC and AUPRC classifier statistics but consumes a lot of memory.
     * (>1GB for holo4k dataset with tessellation=2)
     */
    @RuntimeParam
    boolean stats_collect_predictions = true

    /**
     * produce ROC and PR curve graphs (not fully implemented yet)
     */
    @RuntimeParam
    boolean stats_curves = false

    /**
     * Contact residues distance cutoff (see ContactResiduesPositionFeature)
     */
    @ModelParam
    double feat_crang_contact_dist = 3

    /**
     * probe radius for calculating accessible surface area for asa feature
     */
    @ModelParam
    double feat_asa_probe_radius = 1.4

    /**
     * probe radius for calculating accessible surface area for asa feature
     */
    @ModelParam
    double feat_asa_probe_radius2 = 3

    /**
     * radius of the neighbourhood considered in asa feature
     */
    @ModelParam
    double feat_asa_neigh_radius = 6

    /**
     * radius for calculating of the pmass feature
     */
    @ModelParam
    double feat_pmass_radius = 11

    /**
     * parameter of the pmass feature
     */
    @ModelParam
    int feat_pmass_natoms = 70

    /**
     * parameter of the pmass feature
     */
    @ModelParam
    int feat_pmass_nsasp = 40

    /**
     * selected sub-features in aa index feature
     */
    @ModelParam
    List<String> feat_aa_properties = []

    /**
     * Hyperparameter optimizer implementation ("spearmint" / "pygpgo")
     */
    @RuntimeParam // training
    String hopt_optimizer = "spearmint"

    /**
     * Python command used to run optimization child processes
     */
    @RuntimeParam // training
    String hopt_python_command = "python"

    /**
     * Spearmint home directory (containing main.py)
     */
    @RuntimeParam // training
    String hopt_spearmint_dir = ""

    /**
     * Metric to maximize in hyperparameter optimization.
     * To minimize certain metric use minus sigh prefix, e.g.: "-point_LOG_LOSS"
     */
    @RuntimeParam // training
    String hopt_objective = "DCA_4_0"

    /**
     * max number of iterations in hyperparameter optimization
     */
    @RuntimeParam // training
    int hopt_max_iterations = 1000

    /**
     * randomize seed before every training in experiments
     */
    @RuntimeParam // training
    boolean randomize_seed = false

    /**
     * Most important training/evaluation statistics that will be placed in selected_stats.csv table for easier access.
     * (all stats will be collected anyway)
     */
    @RuntimeParam // training
    List<String> selected_stats = ['DCA_4_0',
                                   'DCA_4_2',
                                   'DCA_4_4',
                                   'DCC_5_0',
                                   'DCC_5_2',
                                   'DSOR_02_0',
                                   'DSOR_02_2',
                                   'DSWO_05_0',
                                   'DSWO_05_2',
                                   'point_MCC',
                                   'point_TPX',
                                   'point_LOG_LOSS',
                                   'AVG_DSO_SUCC',
                                   'AVG_LIGCOV_SUCC',
                                   'AVG_POCKETS',
                                   'AVG_POCKET_SAS_POINTS',
                                   'AVG_POCKET_SAS_POINTS_TRUE_POCKETS',
                                   'TIME_MINUTES']

    /**
     * Path to a JSON file that contains parameters of a transformer from raw score to "z-score calculated from the distribution of true pockets" (pocket.auxInfo.zScoreTP).
     * Use path relative to distro/models/score.
     */
    @RuntimeParam
    String zscoretp_transformer = "default_zscoretp.json"

    /**
     * Path to a JSON file that contains parameters of a transformer from raw score to "probability that pocket with a given score is true pocket" (pocket.auxInfo.probaTP).
     * Use path relative to distro/models/score.
     */
    @RuntimeParam
    String probatp_transformer = "default_probatp.json"

    /**
     * Path to a JSON file that contains parameters of a transformer from raw score to "z-score calculated from the distribution of all residue scores".
     * Use path relative to distro/models/score.
     */
    @RuntimeParam
    String zscoretp_res_transformer = "residue/p2rank_default_zscore.json"

    /**
     * Path to a JSON file that contains parameters of a transformer from raw score to "probability that residue with a given score is true (binding) residue".
     * Use path relative to distro/models/score.
     */
    @RuntimeParam
    String probatp_res_transformer = "residue/p2rank_default_proba.json"

    /**
     * List of pocket score transformers that should be trained (i.e. fitted / inferred) during predict-eval.
     * Transformers are tied to the output distribution of the model (and its parametrization) so new transformers should be trained for every released model.
     * Examples: "ZscoreTpTransformer","ProbabilityScoreTransformer"
     */
    @RuntimeParam
    List<String> train_score_transformers = [] 

    /**
     * Train residue score transformers on a dataset during eval-predict.
     * Transformers are tied to the output distribution of the model (and its parametrization) so new transformers should be trained for every released model.
     */
    @RuntimeParam
    boolean train_score_transformers_for_residues = false


    /**
     * Train model(s) only once in the beginning.
     *
     * Respects value of loop parameter.
     * That is: if loop=10, then 10 models will be trained in the beginning
     * and then average results will be calculated for every step of ploop or hopt run.
     *
     * Relevant only for hyper-parameter optimization (ploop and hopt commands).
     * Makes sense only if optimized hyper-parameters don't influence training and feature extraction.
     */
    @RuntimeParam
    boolean hopt_train_only_once = false

    /**
     * Predict SAS point scores in the eval dataset only once.
     * Relevant only for hyper-parameter optimization (ploop and hopt commands).
     * Makes sense only in combination with hopt_train_only_once=true).
     */
    @RuntimeParam
    boolean hopt_cache_labeled_points = false


    /**
     * Identifies set of pre-calculated propensity tables for duplets/triplets features.
     *
     * Value should be a directory in program resources to take peptide propensities from
     * (resources/tables/propensities/$var/...)
     * Available: SprintT1070, SprintA870
     *
     * TODO: move to dist dir on release
     */
    @ModelParam
    String feat_propensity_tables = "SprintT1070"


    /**
     * When identifying which protein chains are peptides consider provided binary residue labeling (that comes with the dataset).
     */
    @ModelParam // training
    boolean identify_peptides_by_labeling = false

    /**
     * Atoms size threshold for using KD-tree in cutoutSphere routine
     */
    @RuntimeParam
    int use_kdtree_cutout_sphere_thrashold = 150

    /**
     * Directories where to find csv files for csv_file_atom_feature.
     */
    @ModelParam
    List<String> feat_csv_directories = []

    /**
     * Names of enabled value columns from csv files used by csv_file feature. Value columns not listed here are ignored.
     */
    @ModelParam
    List<String> feat_csv_columns = []


    /**
     * If true then csv_file feature ignores:
     * <ul>
     *   <li> missing csv files for proteins
     *   <li> missing value columns
     *   <li> missing rows for atoms or residues
     * <ul>
     */
    @ModelParam
    boolean feat_csv_ignore_missing = false

    /**
     * Structural motifs for stmotif feature.
     * e.g.: C2H2 D1H1 C4 H2
     */
    @ModelParam
    List<String> feat_stmotif_motifs = ["C2H2","C4","C3H1","E1H2","C2H1","H3","D1H2","C3","D1H1","E1H1","C1H3","C2","H2"]

    /**
     * When matching motifs, consider all residues within feat_stmotif_radius around the SAS point.
     * If false, only closest n residues are considered and must match exactly (n = lenght of a motif).
     */
    @ModelParam
    boolean feat_stmotif_useradius = true

    /**
     * Radius related to feat_stmotif_useradius param.
     */
    @ModelParam
    double feat_stmotif_radius = 4d

    /**
     *
     */
    @RuntimeParam
    String chains = "keep"

    @RuntimeParam
    String out_format = "keep"

    @RuntimeParam
    String out_file = null

    /**
     * When using Apo-Holo train dataset enable Apo structures, if false use Holo structures instead.
     */
    @RuntimeParam
    boolean apoholo_use_for_train = false

    /**
     * When using Apo-Holo eval/main dataset enable Apo structures, if false use Holo structures instead.
     */
    @RuntimeParam
    boolean apoholo_use_for_eval = false


//===========================================================================================================//
// Derived parameters
//===========================================================================================================//

    /**
     * Should be (slightly above) the distance of solvent exposed atoms to SAS points.
     */
    double getSasCutoffDist() {
        solvent_radius + surface_additional_cutoff
    }

    /**
     * Derive point labeling from ligands or from labeled residues.
     *
     * @see this.ligand_derived_point_labeling
     */
    boolean derivePointLabelingFromLigands() {
        !predict_residues || ligand_derived_point_labeling || identify_peptides_by_labeling
    }

    int getEffectiveTrainTessellation() {
        (train_tessellation == 0) ? tessellation : train_tessellation
    }

    int getEffectiveTrainTessellationNegatives() {
        (train_tessellation_negatives == 0) ? getEffectiveTrainTessellation() : train_tessellation_negatives
    }

    List<String> getSelectedFeatures() {
        return (features + extra_features).unique()
    }

    double getPointScorePow() {
        if (predict_residues) {
            return (residue_point_score_pow > 0) ? residue_point_score_pow : point_score_pow
        } else {
            return point_score_pow 
        }
    }

//===========================================================================================================//

    /**
     * This method is here so the program version is included in toString() for Params object.
     */
    String getVersion() {
        Main.getVersion()
    }

    /**
     * location of P2Rank installation directory (i.e. directory where the binary and configs and models are / unpacked distro directory)
     */
    String installDir // TODO refactor

//===========================================================================================================//

    /**
     * parameter names and aliases that are not fields in Params class
     * TODO move to better invalid param checking logic
     */
    static final Set<String> EXTRA_PARAMS_AND_ALIASES = ImmutableSet.of(
            'f', 'o',
            'help', 'h',
            'model', 'm',
            'config', 'c',
            'train','t',
            'eval', 'e',
            'label', 'l'
    )

    /**
     * Apply parameter values from the command line
     */
    public updateFromCommandLine(CmdLineArgs args) {

        checkForInvalidArgs(args)
        applyCmdLineArgs(args)

        // processing of special params
        initDependentParams()
    }

    boolean isVaidParamName(String pname) {
        this.metaClass.getMetaProperty(pname) != null
    }

    boolean isVaidParamNameOrAlias(String pname) {
        EXTRA_PARAMS_AND_ALIASES.contains(pname) || isVaidParamName(pname)
    }

    private checkForInvalidArgs(CmdLineArgs args) throws PrankException {
        List<String> argNames = args.namedArgsAndSwitches

        for (String argName : argNames) {
            if (!isVaidParamNameOrAlias(argName)) {
                throw new PrankException("Invalid parameter name: " + argName)
            }
        }
    }

    /**
     * Some parameters have special values that they inherit or otherwise depend on other parameters.
     * ans should be re-initialized any time parameters are loaded.
     */
    void initDependentParams() {
        if (!parallel) {
            threads = 1
            rf_threads = 1
        } else if (threads==1) {
            parallel = false
        }
    }

    @CompileDynamic
    private void applyCmdLineArgs(CmdLineArgs args) {

        boolean filterRanged = args.hasListParams

        Params me = this
        for (String propName : me.properties.keySet()) {
            if (args.namedArgMap.containsKey(propName)) {
                String val = args.get(propName)

                boolean skip = false
                if (filterRanged && ListParam.isIterativeArgValue(propName, val)) {
                    skip = true
                }

                if (!skip) {
                    trySetParam(propName, val)
                }
            } else if (args.switches.contains(propName)) {
                me."$propName" = true
            }
        }
    }

    @CompileDynamic
    public trySetParam(String propertyName, Object value) {
        try {
            setParam(propertyName, value)
        } catch (Exception e) {
            throw new PrankException("Failed to set parameter value. Name: $propertyName, value: '$value'. Reason: " + e.message, e)
        }
    }

    @CompileDynamic
    public setParam(String propertyName, Object value) {

        log.debug "Setting parameter '$propertyName' to '$value'"

        String pname = propertyName
        Object me = this
        Object pv = me."$pname"

        // TODO assign based on real property type not the type of property value bc. it may be null
        
        if (value == null || pv == null) {
            me."$pname" = value
        } else if (pv instanceof String) {
            String v = (String) value
            if (v.startsWith("\"") && v.endsWith("\"")) {
                v = v.substring(1, v.length()-1)
            }
            me."$pname" = v
        } else {
            Class propClass = pv.class

            log.debug "pv class: {}", propClass

            if (pv instanceof List) {
                if (value instanceof List) {
                    me."$pname" = value
                } else {
                    me."$pname" = Sutils.parseList(value)
                }
            } else if (pv instanceof Boolean) {
                me."$pname" = parseBoolean( value )
            } else if (pv instanceof Integer) {
                me."$pname" = new Double(""+value).intValue()
            } else {
                me."$pname" = propClass.valueOf( value )
            }

        }

        log.debug "Property value: '$propertyName' = '${me."$pname"}'"
    }

    private boolean parseBoolean(Object value) {
        if ("false"==value) return false
        if ("0"==value)     return false
        if ("0.0"==value)   return false
        if (0d==value)      return false
        if (0i==value)      return false

        if ("true"==value) return true
        if ("1"==value)    return true
        if ("1.0"==value)  return true
        if (1d==value)     return true
        if (1i==value)     return true

        throw new IllegalArgumentException("Invalid boolean value '$value'")
    }

    @Override
    String toString() {
        return Sutils.toStr(this).replace('=', ' = ') + "\n"
    }

}
