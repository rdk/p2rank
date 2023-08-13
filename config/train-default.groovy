import cz.siret.prank.program.params.Params

/**
 * Allows to re-train default model.
 *
 * Mainly sets some technical parameters ideal for training and evaluating new models.
 * The other parameters of the algorithm should stay as close as possible to the default config.
 */
(params as Params).with {

    dataset_base_dir = "../../p2rank-datasets"

    output_base_dir = "../../p2rank-results/${version}"

    visualizations = false

    delete_models = true

    delete_vectors = true

    max_train_instances = 0

    /**
     * stop processing a dataset on the first unrecoverable error with a dataset item
     */
    fail_fast = false

    seed = 42
    
    loop = 1

    out_prefix_date = false

    crossval_threads = 1

    cache_datasets = true

    clear_prim_caches = false

    clear_sec_caches = false

    feature_importances = false

    output_only_stats = true

    log_cases = true

    log_to_console = false

    log_level = "WARN"

    log_to_file = true

    ploop_delete_runs = true

    ploop_zip_runs = true

    zip_log_file = true

//===========================================================================================================//

    /**
     * collect negatives just from decoy pockets found by other method
     * (alternatively take negative points from all of the protein's surface)
     *
     * Default model was trained with value = true
     */
    sample_negatives_from_decoys = true


    /**
     * Dummy param to preserve behaviour(=bug) of older versions.
     * Should be set to true for training new models.
     */
    atom_table_feat_keep_sgn = false

//===========================================================================================================//

    /**
     * Note: FastRandomForest was used originally to train the default model
     * Setting FasterForest to allow fair comparison with train-conservation config.
     */
    classifier="FasterForest"

//===========================================================================================================//

    rf_flatten = true

}
