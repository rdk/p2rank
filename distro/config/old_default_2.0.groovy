import cz.siret.prank.program.params.Params

/**
 * This config file contains the default configuration of P2Rank running in predict mode (prank predict -f xxxx.pdb).
 *
 * For a full list of all parameters and their default values see Params.groovy in the source code. This file contains only a subset.
 * 
 * Uses old model that were distrubuted with P2Rank 2.0-2.3 .
 */
(params as Params).with {

    /**
     * define this if you want dataset program parameters to be evaluated relative to this directory
     * (an absolute path or path relative to this config file dir, null defaults to the working dir)
     */
    dataset_base_dir = "../test_data"

    /**
     * all output of the program will be stored in subdirectories of this directory
     * (an absolute path or path relative to this config file dir, null defaults to the working dir)
     */
    output_base_dir = "../test_output"

    /**
     * default model
     * (set path relative to install_dir/models/)
     */
    model = "old_default_2.0.model"

    /**
     * Random seed
     */
    seed = 42

    parallel = true

    /**
     * Number of computing threads
     */
    threads = Runtime.getRuntime().availableProcessors() + 1

    /**
     * Number of folds to work on simultaneously
     */
    crossval_threads = 1

    /**
     * defines witch atoms around the ligand are considered to be part of the pocket
     * ligands with longer min. contact distance are considered irrelevant
     */
    ligand_protein_contact_distance = 4

    //== FEATURES

    atom_table_features = ["apRawValids","apRawInvalids","atomicHydrophobicity"]

    features = ["chem","volsite","protrusion","bfactor"]

    atom_table_feat_pow = 2

    /**
     * Dummy param to preserve behaviour of older versions.
     * Should be set to true for training new models.
     *
     * If true sign of value is reapplied after transformation by atom_table_feat_pow
     */
    atom_table_feat_keep_sgn = false

    residue_table_features = [] // ['aa5fact1','aa5fact2','aa5fact3','aa5fact4','aa5fact5']

    protrusion_radius = 10

    //== CLASSIFIERS ===================

    /**
     * see ClassifierOption
     */
    classifier = "FastRandomForest"

    meta_classifier_iterations = 5

    /**
     * works only with classifier "CostSensitive_RF"
     */
    false_positive_cost = 2

    //=== Random Forests =================

    /**
     * RandomForest trees
     */
    rf_trees = 100

    /**
     * RandomForest depth limit, 0=unlimited
     */
    rf_depth = 0

    /**
     * RandomForest feature subset size for one tree, 0=default(sqrt)
     */
    rf_features = 0

    /**
     * number of threads used in RandomForest training (0=use value of threads param)
     */
    rf_threads = 0

    /**
     * cutoff for joining ligand atom groups into one ligand
     */
    ligand_clustering_distance = 1.7 // covalent bond length

    /**
     * cutoff around ligand that defines positives
     */
    positive_point_ligand_distance = 2.6

    /**
     * points between [positive_point_ligand_distance,neutral_point_margin] will be left out form training
     */
    neutral_points_margin = 5.5

    mask_unknown_residues = true

    /**
     * chem. properties representation neighbourhood radius in A
     */
    neighbourhood_radius = 6

    /**
     * HETATM groups that are considered cofactor and ignored
     */
    ignore_het_groups = ["HOH","DOD","WAT","NAG","MAN","UNK","GLC","ABA","MPD","GOL","SO4","PO4"]

    /**
     * positive point defining ligand types, accepted values: "relevant", "ignored", "small", "distant"
     */
    positive_def_ligtypes = ["relevant"]

    /**
     * min. heavy atom count for ligand, other ligands ignored
     */
    min_ligand_atoms = 5

    point_sampler = "SurfacePointSampler"

    /**
     * multiplier for random sampling
     */
    sampling_multiplier = 3

    /**
     * solvent radius for Connolly surface
     */
    solvent_radius = 1.6

    /**
     * SAS tessellation (~density) used in prediction step.
     * Higher tessellation = higher density (+1 ~~ x4 points)
     */
    tessellation = 2

    /**
     * SAS Points tessellation (~= density) used in training step
     */
    train_tessellation = 2

    // for grid and random sampling
    point_min_distfrom_protein = 2.5
    point_max_distfrom_pocket = 4.5

    /* for GridPointSampler */
    grid_cell_edge = 2

    /**
     * Restrict training set size, 0=unlimited
     */
    max_train_instances = 0

    weight_power = 2
    weight_sigma = 2.2
    weight_dist_param = 4.5

    weight_function = "INV"

    deep_surrounding = false

    /** calculate feature vectors from smooth atom feature representation
     * (instead of directly from atom properties)
     */
    smooth_representation = false

    average_feat_vectors = false

    avg_pow = 1

    point_score_pow = 2

    delete_models = true

    delete_vectors = true

    /**
     * number of random seed iterations
     */
    loop = 1

    /**
     * keep datasets (structures and Connolly points) in memory between crossval/seedloop iterations
     */
    cache_datasets = false

    /**
     * calculate feature importance
     * available only for some classifiers
     */
    feature_importances = false

    /**
     * produce pymol visualisations
     */
    visualizations = true

    /**
     * visualize all surface points (not just inner pocket points)
     */
    vis_all_surface = false

    /**
     * copy all protein pdb files to visualization folder (making visualizations portable)
     */
    vis_copy_proteins = true

    /**
     * use strictly inner pocket points or more wider pocket neighbourhood
     */
    strict_inner_points = false

    /**
     * crossvalidation folds
     */
    folds = 5

    /**
     * collect evaluations for top [n+0, n+1,...] pockets (n is true pocket count)
     */
    eval_tolerances = [0,1,2,4,10,99]

    /**
     * make own prank pocket predictions (P2RANK)
     */
    predictions = true

    /**
     * minimum ligandability score for SAS point be considered ligandable
     */
    pred_point_threshold = 0.35

    /**
     * minimum cluster size (of ligandable points) for initial clustering
     */
    pred_min_cluster_size = 3

    /**
     * clustering distance for ligandable clusters for second phase clustering
     */
    pred_clustering_dist = 3

    /**
     * SAS points around ligandable points (an their score) will be included in the pocket
     */
    extended_pocket_cutoff = 3.5

    /**
     * cutoff distance of protein surface atoms considered as part of the pocket
     */
    pred_protein_surface_cutoff = 3.5

    /**
     * Prefix output directory with date and time
     */
    out_prefix_date = false

    /**
     * Place all output files in this sub-directory of the output directory
     */
    out_subdir = null

    /**
     * Balance SAS point score weight by density (points in denser areas will have lower weight)
     */
    balance_density = false

    balance_density_radius = 2

    /**
     * output detailed tables for all proteins, ligands and pockets or residues
     */
    log_cases = true


    /**
     * cutoff for protein exposed atoms calculation (distance from connolly surface is solv.radius. + surf_cutoff)
     */
    surface_additional_cutoff = 2.5

    /**
     * collect negatives just from decoy pockets found by other method
     * (alternatively take negative points from all of the protein's surface)
     */
    sample_negatives_from_decoys = false

    /**
     * cutoff around ligand atoms to select negatives, 0=all
     * valid if training from whole surface (collect_negatives_from_decoys=false)
     */
    train_lig_cutoff = 0

    /**
     * n, use only top-n pockets to select training instances, 0=all
     */
    train_pockets = 9

    /**
     * clear secondary caches (protein surfaces etc.) when iterating params
     */
    clear_sec_caches = false

    /**
     * clear primary caches (protein structures) when iterating params
     */
    clear_prim_caches = false

    /**
     * acceptable distance between ligand center and closest protein atom for relevant ligands
     */
    ligc_prot_dist = 5.5

    rescorer = "ModelBasedRescorer"

    plb_rescorer_atomic = false

    /**
     * stop processing the dataset on the first unrecoverable error with a dataset item
     */
    fail_fast = false

    /**
     * don't produce prediction files for individual proteins (useful for long repetitive experiments)
     */
    output_only_stats = false

    /**
     * Path to a JSON file that contains parameters of a transformer from raw score to "z-score calculated from the distribution of true pockets" (pocket.auxInfo.zScoreTP).
     * Use path relative to distro/models/score.
     */
    zscoretp_transformer = "default_zscoretp.json"

    /**
     * Path to a JSON file that contains parameters of a transformer from raw score to "probability that pocket with a given score is true pocket" (pocket.auxInfo.probaTP).
     * Use path relative to distro/models/score.
     */
    probatp_transformer = "default_probatp.json"

    /**
     * Path to a JSON file that contains parameters of a transformer from raw score to "z-score calculated from the distribution of all residue scores".
     * Use path relative to distro/models/score.
     */
    zscoretp_res_transformer = "residue/p2rank_default_zscore.json"

    /**
     * Path to a JSON file that contains parameters of a transformer from raw score to "probability that residue with a given score is true (binding) residue".
     * Use path relative to distro/models/score.
     */
    probatp_res_transformer = "residue/p2rank_default_proba.json"


    /**
     * added to the cutoff distance around residue in score aggregation from SAS points
     */
    residue_score_extra_dist = 2.0d
    

    /**
     * logging level (TRACE/DEBUG/INFO/WARN/ERROR)
     */
    log_level = "INFO"

    /**
     * print log messages to console
     */
    log_to_console = true

    /**
     * print log messages to file (run.log in outdir)
     */
    log_to_file = true

    /**
     * compress and delete log file at the end (if log_to_file)
     */
    zip_log_file = false

}
