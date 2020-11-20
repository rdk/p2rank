import cz.siret.prank.program.params.Params

/**
 * achieved DCA_4_0 = 0.7909 on joined.ds
 */
(params as Params).with {

    /**
     * define this if you want dataset program parameters to be evaluated relative to this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    dataset_base_dir = "../../p2rank-datasets"

    /**
     * all output of the program will be stored in subdirectories of this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    output_base_dir = "../../p2rank-results/${version}"

    visualizations = false

    delete_models = true

    delete_vectors = true

    max_train_instances = 0

    /**
     * stop processing a datsaset on the first unrecoverable error with a dataset item
     */
    fail_fast = true

    classifier="FasterForest"

    seed = 42

    loop = 10

    predictions = true

    crossval_threads = 5

    /**
     * calculate feature importance
     * available only for some classifiers
     */
    feature_importances = false

    stats_collect_predictions = false

    // technical

    cache_datasets = true

    clear_sec_caches = false

    clear_prim_caches = false

    log_cases = true

    output_only_stats = true

    log_to_console = true

    log_level = "WARN"

    log_to_file = true

    ploop_delete_runs = true

    zip_log_file = true

    out_prefix_date = true

//===========================================================================================================//

    /**
     * collect negatives just from decoy pockets found by other method
     * (alternatively take negative points from all of the protein's surface)
     */
    sample_negatives_from_decoys = false

    atom_table_feat_keep_sgn = true

    atom_table_features = ["ap5sasaValids","ap5sasaInvalids","apRawValids","apRawInvalids","atomicHydrophobicity"]

    features = ["chem","volsite","protrusion","bfactor"]

    residue_table_features = ["RAx"]

    average_feat_vectors = true

//===========================================================================================================//

    rf_trees = 200
    rf_depth = 9
    rf_bagsize = 55

    balance_class_weights = true
    target_class_weight_ratio = 0.4634
    pred_point_threshold = 0.6919

    pred_min_cluster_size = 2
    pred_clustering_dist = 2
    extended_pocket_cutoff = 5.1516
    point_score_pow = 5.8511

    protrusion_radius = 10.3444
    neighbourhood_radius = 5.8294
    positive_point_ligand_distance = 7
    neutral_points_margin = 0
    solvent_radius = 1.8354
    avg_pow = 1
    avg_weighted = true

    weight_function = "INVPOW2"
    weight_power = 0.01
    weight_dist_param = 5
}
