package cz.siret.prank.program.params

import cz.siret.prank.program.Main
import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * global program parameters
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
    String dataset_base_dir = null

    /**
     * all output of the program will be stored in subdirectores of this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    String output_base_dir = null

    /**
     * serialized model
     * (set path relative to install_dir/models/)
     */
    String model = "default.model"

    /**
     * Random seed
     */
    int seed = 42

    boolean parallel = true

    /**
     * Number of computing threads
     */
    int threads = Runtime.getRuntime().availableProcessors() + 1

    /**
     *  Number for threads for generating R plots
     */
    int r_threads = 2

    /**
     * Number of folds to work on simultaneously
     */
    int crossval_threads = 1 // Math.min(5, Runtime.getRuntime().availableProcessors())

    /**
     * defines witch atoms around the ligand are considered to be part of the pocket
     * (ligands with longer distance are considered irrelevant floating ligands)
     */
    double ligand_protein_contact_distance = 4

    //== FAETURES

    List<String> extra_features = ["protrusion","bfactor"]

    List<String> atom_table_features = ["apRawValids","apRawInvalids","atomicHydrophobicity"] // "ap5sasaValids","ap5sasaInvalids"

    double atom_table_feat_pow = 2

    /**
     * dummy param to preserve behaviour of older versions
     */
    boolean atom_table_feat_keep_sgn = false

    List<String> residue_table_features = [] // ['aa5fact1','aa5fact2','aa5fact3','aa5fact4','aa5fact5']

    double protrusion_radius = 10

//===========================================================================================================//


    /**
     * Number of bins for protr_hist feature, must be >=2
     */
    int protr_hist_bins = 5

    boolean protr_hist_cumulative = false

    boolean protr_hist_relative = false

//===========================================================================================================//

    /**
     * Number of bins for Atom Pair distance histogram (pair_hist) feature, must be >=2
     */
    int pair_hist_bins = 5

    /**
     * Radius capturing atoms considered in pair_hist feature
     */
    double pair_hist_radius = 6

    /**
     * smooth vs. sharp binning
     */
    boolean pair_hist_smooth = false

    boolean pair_hist_normalize = false

    /**
     * if false only protein exposed atmos are considered
     */
    boolean pair_hist_deep = true

    /**
     * size of random subsample of atom pairs, 0 = all
     */
    int pair_hist_subsample_limit = 0

//===========================================================================================================//

    /**
     * conservation parameteres
     */
    boolean load_conservation = false // always load conservation (for stats)

    String score_pockets_by = "p2rank" // possible values: "p2rank", "conservation", "combi"

    /**
     * Conservation exponent for rescoring pockets
     */
    int conservation_exponent = 1

    double conserv_cloud_radius = 10

    double ss_cloud_radius = 10

    /**
     * Conservation file with this pattern is loaded:
     * baseName + chainId + "." + origin + ".hom.gz"
     */
    String conservation_origin = "hssp"

    /**
     * Directory in which to look for conservation score files.
     * Path relative to dataset directory.
     * if null: look in the same directory as protein file
     */
    String conservation_dir = null

    /**
     * Log scores for binding and nonbinding scores to file
     */
    String log_scores_to_file = ""

    /**
     * limits how many pocket SAS points are used for scoring (after sorting), 0=unlimited
     * affects scoring pockets and also residues
     */
    int score_point_limit = 0

//===========================================================================================================//


    //== CLASSIFIERS ===================

    /**
     * see ClassifierOption
     */
    String classifier = "FastRandomForest"

    /**
     * see ClassifierOption
     */
    String inner_classifier = "FastRandomForest"

    int meta_classifier_iterations = 5

    /**
     * works only with classifier "CostSensitive_RF"
     */
    double false_positive_cost = 2

    //=== Random Forests =================

    /**
     * RandomForest trees
     */
    int rf_trees = 100

    /**
     * RandomForest depth limit, 0=unlimited
     */
    int rf_depth = 0

    /**
     * RandomForest feature subset size for one tree, 0=default(sqrt)
     */
    int rf_features = 0

    /**
     * number of threads used in RandomForest training (0=use value of threads param)
     */
    int rf_threads = 0

    /**
     * size of a bag: 1..100% of the dataset
     */
    int rf_bagsize = 100

    /**
     * cutoff for joining ligand atom groups into one ligand
     */
    double ligand_clustering_distance = 1.7 // ~ covalent bond length

    /**
     * cutoff around ligand that defines positives
     */
    double positive_point_ligand_distance = 2.5

    /**
     * distance around ligand atoms that define ligand induced volume
     * (for evaluation by some criteria, DSO, ligand coverage...)
     */
    double ligand_induced_volume_cutoff = 2.5

    /**
     * points between [positive_point_ligand_distance,neutral_point_margin] will be left out form training
     */
    double neutral_points_margin = 5.5

    boolean mask_unknown_residues = true

    /**
     * chem. properties representation neighbourhood radius in A
     */
    double neighbourhood_radius = 8

    /**
     * HETATM groups that are considered cofactor and ignored
     */
    Set<String> ignore_het_groups = ["HOH","DOD","WAT","NAG","MAN","UNK","GLC","ABA","MPD","GOL","SO4","PO4"] as Set

    /**
     * positive point defining ligand types accepted values: "relevant", "ignored", "small", "distant"
     */
    List<String> positive_def_ligtypes = ["relevant"]

    /**
     * min. heavy atom count for ligand, other ligands ignored
     */
    int min_ligand_atoms = 5

    String point_sampler = "SurfacePointSampler"

    /**
     * multiplier for random point sampling
     */
    int sampling_multiplier = 3

    /**
     * solvent radius for SAS surface
     */
    double solvent_radius = 1.6

    /**
     * SAS tessellation (~density) used in pradiction step
     */
    int tessellation = 2

    /**
     * SAS tessellation (~density) used in training step
     */
    int train_tessellation = 2

    // for grid and random sampling
    double point_min_distfrom_protein = 2.5
    double point_max_distfrom_pocket = 4.5

    /* for GridPointSampler */
    double grid_cell_edge = 2

    /**
     * Restrict training set size, 0=unlimited
     */
    int max_train_instances = 0

    double weight_power = 2
    double weight_sigma = 2.2
    double weight_dist_param = 4.5

    String weight_function = "INV"

    boolean deep_surrounding = false

    /** calculate feature vectors from smooth atom feature representation
     * (instead of directly from atom properties)
     */
    boolean smooth_representation = false

    double smoothing_radius = 4.5

    /**
     * determines how atom feature vectors are projected on to SAS point feature vector
     * if true, atom feature vectors are averaged
     * else they are only summed up
     */
    boolean average_feat_vectors = false

    /**
     * in feature projection from atoms to SAS points:
     * only applicable when average_feat_vectors=true
     * <0,1> goes from 'no average, just sum' -> 'full average'
     */
    double avg_pow = 1

    /**
     * regarding feature projection from atoms to SAS points: calculate weighted average
     * (shoud be true by default, kept false for backward compatibility reasons)
     */
    boolean avg_weighted = false

    /**
     * exponent of point ligandabitity score (before adding it to pocket score)
     */
    double point_score_pow = 2

    /**
     * Binary classifiers produces historgam of scores for class0 and class1
     * if true only score for class1 is considered
     * makes a difference only if histogram produced by classifier doesn't sum up to 1
     */
    boolean use_only_positive_score = true

    boolean delete_models = false

    /**
     * delete files containing training/evaluation feature vectors
     */
    boolean delete_vectors = true

    boolean check_vectors = false

    /**
     * collect vectors also from eval dataset (only makes sense if delete_vectors=false)
     */
    boolean collect_eval_vectors = false

    /**
     * collect vectors only at the beginning of seed loop routine
     * if dataset is subsampled (using train_protein_limit param) then dataset is subsampled only once
     * set to false when doing learning curve!
     * train_protein_limit>0 should be always paired with collect_only_once=false
     */
    boolean collect_only_once = true

    /**
     * number of random seed iterations
     */
    int loop = 1

    /**
     * keep datasets (structures and SAS points) in memory between crossval/seedloop iterations
     */
    boolean cache_datasets = false

    /**
     * calculate feature importances
     * available only for some classifiers
     */
    boolean feature_importances = false

    /**
     * produce pymol visualisations
     */
    boolean visualizations = true

    /**
     * visualize all surface points (not just inner pocket points)
     */
    boolean vis_all_surface = false

    /**
     * copy all protein pdb files to visualization folder (making visualizations portable)
     */
    boolean vis_copy_proteins = true

    /**
     * generate new protein pdb files from structures in memory instead of reusing input files
     * (useful when structures were manipulated in memory, e.g. when reducing to specified chains)
     */
    boolean vis_generate_proteins = true

    /**
     * zip PyMol visualizations to save space
     */
    boolean zip_visualizations = false

    /**
     * use strictly inner pocket points or more wider pocket neighbourhood
     */
    boolean strict_inner_points = false

    /**
     * crossvalidation folds
     */
    int folds = 5

    /**
     * collect evaluations for top [n+0, n+1,...] pockets (n is true pocket count)
     */
    List<Integer> eval_tolerances = [0,1,2,4,10,99]

    /**
     * make own prank pocket predictions (P2RANK)
     */
    boolean predictions = true

    /**
     * residue prediction mode (opposed to pocket prediction)
     */
    boolean predict_residues = false

    /**
     * (in predict mode) produce residue labeling file
     */
    boolean label_residues = true

    /**
     * residue score threshold fir calculating predicted binary label
     */
    double residue_score_threshold = 1d

    /**
     * in calculation of residue score from neighboring SAS points:
     * <0,1> goes from 'no average, just sum' -> 'full average'
     */
    double residue_score_sum_to_avg = 0d

    /**
     * added to the cutoff distance around residue in score aggregation from SAS points
     */
    double residue_score_extra_dist = 0d

    /**
     * minimum ligandability score for SAS point to be considered ligandable
     */
    double pred_point_threshold = 0.4

    /**
     * minimum cluster size (of ligandable points) for initial clustering
     */
    int pred_min_cluster_size = 3

    /**
     * clustering distance for ligandable clusters for second phase clustering
     */
    double pred_clustering_dist = 5

    /**
     * SAS points around ligandable points (an their score) will be included in the pocket
     */
    double extended_pocket_cutoff = 3.5

    /**
     * cuttoff distance of protein surface atoms considered as part of the pocket
     */
    double pred_protein_surface_cutoff = 3.5

    /**
     * Prefix output directory with date and time
     */
    boolean out_prefix_date = false

    /**
     *
     */
    String out_subdir = null

    /**
     * balance SAS point score weight by density
     */
    boolean balance_density = false

    double balance_density_radius = 2

    /**
     * output detailed tables for all proteins, ligands and pockets
     */
    boolean log_cases = false

    /**
     * cutoff for protein exposed atoms calculation (distance from SAS surface is solv.radius. + surf_cutoff)
     */
    double surface_additional_cutoff = 1.8

    /**
     * collect negatives just from decoy pockets found by other method
     * (alternatively take negative points from all of the protein's surface)
     */
    boolean sample_negatives_from_decoys = false

    /**
     * cutoff atound ligand atoms to select negatives, 0=all
     * valid if training from whole surface (sample_negatives_from_decoys=false)
     */
    double train_lig_cutoff = 0

    /**
     * n, use only top-n pockets to select training instances, 0=all
     */
    int train_pockets = 0

    /**
     * clear primary caches (protein structures) between runs (when iterating params or seed)
     */
    boolean clear_prim_caches = false

    /**
     * clear secondary caches (protein surfaces etc.) between runs (when iterating params or seed)
     */
    boolean clear_sec_caches = false



    /**
     * acceptable distance between ligand center and closest protein atom for relevant ligands
     */
    double ligc_prot_dist = 5.5

    /**
     * pocket rescoring algorithm PRANK="ModelBasedRescorer"
     */
    String rescorer = "ModelBasedRescorer"

    boolean plb_rescorer_atomic = false

    /**
     * stop processing the datsaset on the first unrecoverable error with a dataset item
     */
    boolean fail_fast = false

    /**
     * target class ratio of positives/negatives we train on.
     * relates to subsampling and supersampling
     */
    double target_class_ratio = 0.1

    /**
     * in training use subsampling to deal with class imbalance
     */
    boolean subsample = false

    /**
     * in training use supersampling to deal with class imbalance
     */
    boolean supersample = false

    /**
     * sort negatives desc by protrusion before subsampling
     */
    boolean subsampl_high_protrusion_negatives = false

    /**
     * don't produce prediction files for individual proteins (useful for long repetitive experiments)
     */
    boolean output_only_stats = false

    /**
     * compress results of individual ploop runs
     */
    boolean ploop_zip_runs = true

    /**
     * delete results of individual ploop/hopt runs
     */
    boolean ploop_delete_runs = true


    /**
     * logging level (TRACE/DEBUG/INFO/WARN/ERROR)
     */
    String log_level = "INFO"

    /**
     * print log messages to console
     */
    boolean log_to_console = true

    /**
     * print log messages to file (run.log in outdir)
     */
    boolean log_to_file = true

    /**
     * compress and delete log file at the end (if log_to_file)
     */
    boolean zip_log_file = false

    /**
     * limit the number of proteins that used for training. random subset of proteins from the dataset is used each run in seedloop
     * 0 = no limit
     */
    int train_protein_limit = 0

    /**
     * add weights to instances to achieve target_weight_ratio (if classifier algorithm supports it)
     *
     */
    boolean balance_class_weights = false

    /**
     * target ratio of weighted sums of positive/negative instances when balancing class weights (balance_class_weights=true)
     */
    double target_class_weight_ratio = 0.1

    /**
     * produce classifier stats also for train dataset
     */
    boolean classifier_train_stats = false

    /**
     * Collect predictions for all points in the dataset.
     * Allows calculation of AUC and AUPRC classifier statistics but consumes a lot of memory.
     * (>1GB for holo4k dataset with tesselation=2)
     */
    boolean stats_collect_predictions = false

    /** produce ROC and PR curve graphs (not fully implemented yet) */
    boolean stats_curves = false

    /**
     * Contact residues distance cutoff
     */
    double feat_crang_contact_dist = 3

    /**
     * probe radius for calculating accessible surface area for asa feature
     */
    double feat_asa_probe_radius = 1.4

    /**
     * probe radius for calculating accessible surface area for asa feature
     */
    double feat_asa_probe_radius2 = 1.4

    /**
     * radius of the neighbourhood considered in asa feature
     */
    double feat_asa_neigh_radius = 6

    double feat_pmass_radius = 11

    int feat_pmass_natoms = 70
    
    int feat_pmass_nsasp = 40

    /**
     * selected sub-features in aa index feature
     */
    List<String> feat_aa_properties = null

    /**
     * Hyperparameter optimizer implementation (so far only "spearmint")
     */
    String hopt_optimizer = "spearmint"

    /**
     * Spearmint home directory (containing main.py)
     */
    String hopt_spearmint_dir = ""

    /**
     * Metric to minimize in hyperparameter optimization
     * (minus sign allowed)
     */
    String hopt_objective = "-DCA_4_0"

    /**
     * max number of iterations in hyperparameter optimization
     */
    int hopt_max_iterations = 1000

    /**
     * randomize seed before every training in experiments
     */
    boolean randomize_seed = false

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
    String zscoretp_transformer = "default_zscoretp.json"

    /**
     * Path to json file that contains parameters of transformation of raw score to "probability that pocket with given score is true pocket" (pocket.auxInfo.probaTP).
     * Use path relative to distro/models/score.
     */
    String probatp_transformer = "default_probatp.json"


    /**
     * Path to json file that contains parameters of transformation of raw score to "z-score calculated from distribution of all residue scores".
     * Use path relative to distro/models/score.
     */
    String zscoretp_res_transformer = "residue/p2rank_default_zscore.json"

    /**
     * Path to json file that contains parameters of transformation of raw score to "probability that residue with given score is true residue".
     * Use path relative to distro/models/score.
     */
    String probatp_res_transformer = "residue/p2rank_default_proba.json"

    List<String> train_score_transformers = [] // ["ZscoreTpTransformer","ProbabilityScoreTransformer"]

    /**
     * Train resaidue score transformers on a dataset during predict-eval
     */
    boolean train_score_transformers_for_residues = false

    /**
     * Reduce loaded protein structures to chains declared in dataset file (in optional chains column)
     */
    boolean load_only_specified_chains = false

    /**
     * In hyperparameter optimization (ploop and hopt commands) train model only once in the beginning
     * (makes sense if optimized hyperparameters do't influence training and feature extraction)
     */
    boolean hopt_train_only_once = false

    /**
     * directory in program resources to take peptide propensities from
     * (resources/tables/peptides/$var/...)
     * Available: SprintT1070, SprintA870
     * TODO: move to dist dir
     */
    String pept_propensities_set = "SprintT1070"

    boolean identify_peptides_by_labeling = false

    /**
     * Atoms size threshold for using KD-tree in cutoutSphere routine
     */
    int use_kdtree_cutout_sphere_thrashold = 150

//===========================================================================================================//

    /**
     * Should be (slightly above) the distence of solvent exposed atoms to SAS points
     * @return
     */
    double getSasCutoffDist() {
        solvent_radius + surface_additional_cutoff
    }

//===========================================================================================================//

    String getVersion() {
        Main.getVersion()
    }

    String installDir // TODO refactor

//===========================================================================================================//

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
    void applyCmdLineArgs(CmdLineArgs args) {

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
        
        if (pv == null) {
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
