import cz.siret.prank.program.params.Params

(params as Params).with {

    /**
     * define this if you want dataset program parameters to be evaluated relative to this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    dataset_base_dir = "../../p2rank-datasets"

    /**
     * all output of the prorgam will be stored in subdirectores of this directory
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

    classifier="FastRandomForest"

    seed = 42

    loop = 10

    predictions = true

    out_prefix_date = false

    crossval_threads = 5

    cache_datasets = true

    clear_sec_caches = false

    clear_prim_caches = false


    log_cases = true

    /**
     * calculate feature importance
     * available only for some classifiers
     */
    feature_importances = false

    output_only_stats = true

    /**
     * collect negatives just from decoy pockets found by other method
     * (alternatively take negative points from all of the protein's surface)
     */
    sample_negatives_from_decoys = false

    atom_table_feat_keep_sgn = true

    atom_table_features = ["ap5sasaValids","ap5sasaInvalids","apRawValids","apRawInvalids","atomicHydrophobicity"]

    extra_features = ["protrusion","bfactor"]

    residue_table_features = ["RAx"]

    log_to_console = false

    log_to_file = true

    ploop_delete_runs = true

    zip_log_file = true

}
