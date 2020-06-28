package cz.siret.prank.program.params

import cz.siret.prank.program.Main
import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Holds all global parameters of the program.
 *
 * This file is also main source of parameter description/documenmtation.
 *
 * Parameter annotations:
 * @RuntimeParam            ... Parameters related to program execution.
 * @ModelParam              ... Actual parameters of the algorithm, related to extracting features and calculating results.
 *                              It is important that those parameters stay the same when training a model and then using it for inference.
 * @ModelParam // training  ... Model params used only in training phase but not during inference.
 */
@CompileStatic
@Slf4j
class Params {

    public static final Params INSTANCE = new Params()

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
     * (ligands with longer distance are considered irrelevant floating ligands)
     */
    @ModelParam // training
    double ligand_protein_contact_distance = 4

    //==[ Features ]=========================================================================================================//

    /**
     * List of general calculated features
     */
    @ModelParam
    List<String> extra_features = ["protrusion","bfactor"]

    /**
     * List of features that come directly from atom type table
     * see atomic-properties.csv
     */
    @ModelParam
    List<String> atom_table_features = ["apRawValids","apRawInvalids","atomicHydrophobicity"]

    /**
     * List of features that come directly from residue table
     */
    @ModelParam
    List<String> residue_table_features = []

    /**
     * Exponent applied to all atom table features
     */
    @ModelParam
    double atom_table_feat_pow = 2

    /**
     * dummy param to preserve behaviour of older versions
     * if true sign of value is reapplied after transformation by atom_table_feat_pow
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
     * Conservation file with this pattern is loaded:
     * baseName + chainId + "." + origin + ".hom.gz"
     */
    @RuntimeParam
    String conservation_origin = "hssp"

    /**
     * Directory in which to look for conservation score files.
     * Path relative to dataset directory.
     * if null: look in the same directory as protein file
     */
    @RuntimeParam
    String conservation_dir = null

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
    Set<String> ignore_het_groups = ["HOH","DOD","WAT","NAG","MAN","UNK","GLC","ABA","MPD","GOL","SO4","PO4"] as Set

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
     * Others like GridPointSampler are experimental.
     */
    @ModelParam
    String point_sampler = "SurfacePointSampler"

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
     */
    @ModelParam // training
    int train_tessellation = 2

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
     * grid cell size for GridPointSampler
     */
    @ModelParam
    double grid_cell_edge = 2

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
    @ModelParam
    boolean smooth_representation = false

    /**
     * related to smooth_representation
     */
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
     * set to false when doing learning curve!
     * train_protein_limit>0 should be always paired with collect_only_once=false
     */
    @RuntimeParam
    boolean collect_only_once = true

    /**
     * number of random seed iterations
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
     */
    @ModelParam
    double residue_score_extra_dist = 0d

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
     * output detailed tables for all proteins, ligands and pockets
     */
    @RuntimeParam
    boolean log_cases = false

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
     * acceptable distance between ligand center and closest protein atom for relevant ligands
     */
    @ModelParam // training
    double ligc_prot_dist = 5.5

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
    boolean ploop_zip_runs = true

    /**
     * delete results of individual ploop/hopt runs
     */
    @RuntimeParam
    boolean ploop_delete_runs = true

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
    boolean stats_collect_predictions = false

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
    double feat_asa_probe_radius2 = 1.4

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
     * Hyperparameter optimizer implementation (so far only "spearmint")
     */
    @RuntimeParam // training
    String hopt_optimizer = "spearmint"

    /**
     * Spearmint home directory (containing main.py)
     */
    @RuntimeParam // training
    String hopt_spearmint_dir = ""

    /**
     * Metric to minimize in hyperparameter optimization
     * (minus sign allowed)
     */
    @RuntimeParam // training
    String hopt_objective = "-DCA_4_0"

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
                                   'MCC',
                                   'TPX',
                                   'LOGLOSS',
                                   'AVG_DSO_SUCC',
                                   'AVG_LIGCOV_SUCC',
                                   'AVG_POCKETS',
                                   'AVG_POCKET_SAS_POINTS',
                                   'AVG_POCKET_SAS_POINTS_TRUE_POCKETS',
                                   'TIME_MINUTES']

    /**
     * Path to json file that contains parameters of transformation of raw score to "z-score calculated from distribution of true pockets" (pocket.auxInfo.zScoreTP).
     * Use path relative to distro/models/score.
     */
    @RuntimeParam
    String zscoretp_transformer = "default_zscoretp.json"

    /**
     * Path to json file that contains parameters of transformation of raw score to "probability that pocket with given score is true pocket" (pocket.auxInfo.probaTP).
     * Use path relative to distro/models/score.
     */
    @RuntimeParam
    String probatp_transformer = "default_probatp.json"

    /**
     * Path to json file that contains parameters of transformation of raw score to "z-score calculated from distribution of all residue scores".
     * Use path relative to distro/models/score.
     */
    @RuntimeParam
    String zscoretp_res_transformer = "residue/p2rank_default_zscore.json"

    /**
     * Path to json file that contains parameters of transformation of raw score to "probability that residue with given score is true (binding) residue".
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
     * Train residue score transformers on a dataset during predict-eval.
     * Transformers are tied to the output distribution of the model (and its parametrization) so new transformers should be trained for every released model.
     */
    @RuntimeParam
    boolean train_score_transformers_for_residues = false


    /**
     * In hyper-parameter optimization (ploop and hopt commands) train model only once in the beginning
     * (makes sense if optimized hyper-parameters don't influence training and feature extraction)
     */
    @RuntimeParam
    boolean hopt_train_only_once = false

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
     * Used by csv_file_atom_feature.
     */
    @RuntimeParam
    List<String> feat_csv_directories = [];

//===========================================================================================================//

    /**
     * Derived parameter.
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
     * Apply parameter values from the command line
     */
    public updateFromCommandLine(CmdLineArgs args) {

        applyCmdLineArgs(args)

        // processing of special params
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
        me.properties.keySet().each { String propName ->
            if (args.namedArgMap.containsKey(propName)) {
                String val = args.get(propName)

                boolean skip = false
                if (filterRanged && ListParam.isListArgValue(val)) {
                    skip = true
                }

                if (!skip) {
                    setParam(propName, val)
                }
            } else if (args.switches.contains(propName)) {
                me."$propName" = true
            }
        }
    }

    @CompileDynamic
    public setParam(String propertyName, Object value) {

        log.debug "Setting '$propertyName' to '$value'"

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
                if ("0"==value) value=false
                if ("0.0"==value) value=false
                if (0d==value) value=false
                if ("1"==value) value=true
                if ("1.0"==value) value=true
                if (1d==value) value=true
                me."$pname" = Boolean.valueOf( value )
            } else if (pv instanceof Integer) {
                me."$pname" = new Double(""+value).intValue()
            } else {
                me."$pname" = propClass.valueOf( value )
            }

        }

        log.debug "Property value: '$propertyName' = '${me."$pname"}'"
    }

    @Override
    String toString() {
        return Sutils.toStr(this).replace('=', ' = ') + "\n"
    }

}
