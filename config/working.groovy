import cz.siret.prank.program.params.Params

/**
 * This config file is setting technical parameters ideal for training and evaluating new models.
 *
 * In other parameters of the algorithm should stay as close as possible do default config.
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
     * stop processing a dataset on the first unrecoverable error with a dataset item
     */
    fail_fast = true

    classifier="FasterForest"

    seed = 42
    loop = 10



    out_prefix_date = false

    crossval_threads = 1

    cache_datasets = true

    clear_prim_caches = false

    clear_sec_caches = false


    /**
     * calculate feature importance
     * available only for some classifiers
     */
    feature_importances = false

    output_only_stats = true

    log_cases = true

    log_to_console = false

    log_level = "WARN"

    log_to_file = true

    ploop_delete_runs = true

    zip_log_file = true

    /**
     * collect negatives just from decoy pockets found by other method
     * (alternatively take negative points from all of the protein's surface)
     *
     * here set to true for backwards compatibility for training on *-fpocket.ds datasets in misc/test-scripts/testsets.sh script
     */
    sample_negatives_from_decoys = true


}
