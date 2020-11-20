import cz.siret.prank.program.params.Params

/**
 * 
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


    residue_table_features = ["RAx"]

    average_feat_vectors = true

//===========================================================================================================//

    balance_class_weights = true
    neutral_points_margin = 0
    avg_weighted = true
    weight_function = "INVPOW2"

    features = ["chem","volsite","protrusion","bfactor", "pmass"]
    rf_trees =	50
    rf_depth	=  12
    rf_features	=  26
    rf_bagsize	=  90
    target_class_weight_ratio	=   0.4067
    pred_point_threshold	=   0.8808
    pred_min_cluster_size	=   2.0000
    pred_clustering_dist	=   2.5000
    score_point_limit	= 100.0000
    tessellation	=   3.0000
    extended_pocket_cutoff	=   1.0000
    point_score_pow	=   0.1088
    protrusion_radius	=  13.8710
    neighbourhood_radius	=   8.0000
    positive_point_ligand_distance	=   3.1510
    solvent_radius	=   1.4090
    avg_pow	=   0.0000
    weight_power	=   0.1000

}
