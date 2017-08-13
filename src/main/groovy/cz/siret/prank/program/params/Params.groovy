package cz.siret.prank.program.params

import cz.siret.prank.program.Main
import cz.siret.prank.utils.CmdLineArgs
import cz.siret.prank.utils.StrUtils
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
     * all output of the prorgam will be stored in subdirectores of this directory
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

    /**
     * Number of bins for protr_hist feature, must be >=2
     */
    int protr_hist_bins = 5

    boolean protr_hist_cumulative = false

    boolean protr_hist_relative = false

    /**
     * Number of bins for pair_hist feature, must be >=2
     */
    int pair_hist_bins = 5

    double pair_hist_radius = 6

    boolean pair_hist_smooth = false

    boolean pair_hist_normalize = false

    boolean pair_hist_deep = true
    

    /**
     * conservation parameteres
     */
    boolean load_conservation = false // always load conservation (for stats)

    String score_pockets_by = "p2rank" // possible values: "p2rank", "conservation", "combi"

    /**
     * Conservation exponent for rescoring pockets
     */
    int conservation_exponent = 1

    /**
     * Conservation file with this pattern is loaded:
     * baseName + chainId + "." + origin + ".hom.gz"
     */
    String conservation_origin = "hssp";

    /**
     * Log scores for binding and nonbinding scores to file
     */
    String log_scores_to_file = "";

    //== CLASSIFIERS ===================

    /**
     * see ClassifierOption
     */
    String classifier = "FastRandomForest"

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
     * positive point defining ligand types acceped values: "relevant", "ignored", "small", "distant"
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
     * solvent radius for Connolly surface
     */
    double solvent_radius = 1.6

    /**
     * Connolly point tessellation (~density) used in pradiction step
     */
    int tessellation = 2

    /**
     * Connolly point tessellation (~density) used in training step
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

    double avg_pow = 1

    /**
     * regarding feature projection to SAS points: calculate weighted average
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

    /**
     * number of random seed iterations
     */
    int loop = 1

    /**
     * keep datasets (structures and Connolly points) in memory between crossval/seedloop iterations
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
     * zip PyMol visualizations to save space
     */
    boolean zip_visualizations = false

    /**
     * use sctrictly inner pocket points or more wider pocket neighbourhood
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
     * minimum ligandability score for Connolly point to be considered ligandable
     */
    double pred_point_threshold = 0.4

    boolean include_surrounding_score = false

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
     * balance Connolly point score weight by density
     */
    boolean balance_density = false

    double balance_density_radius = 2

    /**
     * output detailed tables for all proteins, ligands and pockets
     */
    boolean log_cases = false

    /**
     * cutoff for protein exposed atoms calculation (distance from connolly surface is solv.radius. + surf_cutoff)
     */
    double surface_additional_cutoff = 1.8

    /**
     * collect negatives just from decoy pockets found by other method
     * (alternatively take negative points from all of the protein's surface)
     */
    boolean sample_negatives_from_decoys = true

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
    boolean clear_sec_caches = true



    /**
     * acceptable distance between ligand center and closest protein atom for relevant ligands
     */
    double ligc_prot_dist = 5.5

    String rescorer = "WekaSumRescorer"

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

    boolean subsample = false

    /**
     *
     */
    boolean supersample = false


    /**
     * sort negatives desc by protrusion before subsampling
     */
    boolean subsampl_high_protrusion_negatives = false

    /**
     * don't procuce prediction files for individual proteins (useful for long repetitive experiments)
     */
    boolean output_only_stats = false

    /**
     * compress results of individual ploop runs
     */
    boolean ploop_zip_runs = true

    /**
     * delete results of individual ploop runs
     */
    boolean ploop_delete_runs = false


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
    boolean log_to_file = false

    /**
     * cmompress and delete log file at the end (if log_to_file)
     */
    boolean zip_log_file = false

    /**
     * limit the number of proteins that used for training. random subset of proteins from the dataset is used each run in seedloop
     * 0 = no limit
     */
    int train_ptorein_limit = 0

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

    /**
     * Hyperparameter optimizer implementation (so far only "spearmint")
     */
    String hopt_optimizer = "spearmint"

    /**
     * Spearmint home directory (containing main.py)
     */
    String hopt_spearmint_dir = ""

    /**
     * Statistic to minimize
     * (minus sign allowed)
     */
    String hopt_objective = "-DCA_4_0"

    /**
     * number of inetarions
     */
    int hopt_max_iterations = 100

    /**
     * randomize seed before every training in experiments
     */
    boolean randomize_seed = false

    List<String> selected_stats = ['DCA_4_0', 'DCA_4_2', 'DCA_4_4', 'AVG_POCKETS', 'AVG_POCKET_SAS_POINTS', 'AVG_POCKET_SAS_POINTS_TRUE_POCKETS', 'TIME_MINUTES']

//===========================================================================================================//

    String getVersion() {
        Main.getVersion()
    }

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
                    me."$pname" = StrUtils.parseList(value)
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
        return StrUtils.toStr(this).replace('=', ' = ') + "\n"
    }

}
