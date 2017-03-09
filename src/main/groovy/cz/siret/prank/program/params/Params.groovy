package cz.siret.prank.program.params

import cz.siret.prank.program.Main
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import cz.siret.prank.features.weight.WeightFun
import cz.siret.prank.utils.StrUtils
import cz.siret.prank.utils.CmdLineArgs

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
     * Number of folds to work on simultaneously
     */
    int crossval_threads = 1 // Math.min(5, Runtime.getRuntime().availableProcessors())

    /**
     * defines witch atoms around the ligand are considered to be part of the pocket
     * (ligands with longer distance are considered irrelevant floating ligands)
     */
    double ligand_protein_contact_distance = 4

    //== FAETURES

    /**
     * include volsite pharmacophore properties
     */
    boolean use_volsite_features = true

    List<String> extra_features = ["protrusion","bfactor"]

    List<String> atom_table_features = ["apRawValids","apRawInvalids","atomicHydrophobicity"] // "ap5sasaValids","ap5sasaInvalids"

    double atom_table_feat_pow = 2

    /**
     * dummy param to preserve behaviour of older versions
     */
    boolean atom_table_feat_keep_sgn = false

    List<String> residue_table_features = [] // ['aa5fact1','aa5fact2','aa5fact3','aa5fact4','aa5fact5']

    double protrusion_radius = 10

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
     * cutoff for joining ligand atom groups into one ligand
     */
    double ligand_clustering_distance = 1.7 // covalent bond length

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

    boolean average_feat_vectors = false

    double avg_pow = 1

    double point_score_pow = 2

    boolean delete_models = false

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
    boolean predictions = false

    /**
     * minimum ligandability score for Connolly point to be considered ligandable
     */
    double pred_point_threshold = 0.4

    /**
     * minimum cluster size (of ligandable points) for initial clustering
     */
    double pred_min_cluster_size = 3

    /**
     * clustering distance for ligandable clusters for second phase clustering
     */
    double pred_clustering_dist = 5

    /**
     * distance to extend clusters around hotspots
     */
    double pred_surrounding = 3.5

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

        boolean filterRanged = args.hasRangedParams

        Params me = this
        me.properties.keySet().each { String propName ->
            if (args.namedArgMap.containsKey(propName)) {
                String val = args.get(propName)

                boolean skip = false
                if (filterRanged && RangeParam.isRangedArgValue(val)) {
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
        if (me."$pname" instanceof String || me."$pname" == null) {
            me."$pname" = value
        } else {
            Object pv = me."$pname"
            Class propClass = pv.class

            me.properties

            if (pv instanceof List) {
                if (value instanceof List) {
                    me."$pname" = value
                } else {
                    me."$pname" = StrUtils.parseList(value)
                }
            } else if (pv instanceof Boolean) {
                if ("0"==value) value=false
                if ("1"==value) value=true
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
