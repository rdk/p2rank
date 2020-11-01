import cz.siret.prank.program.params.Params

/**
 * Config file for testing AA index
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

    feat_aa_properties = ['SUYM030101',
                          'CASG920101',
                          'BAEK050101',
                          'RACS820107',
                          'GUYH850102',
                          'DESM900101',
                          'KOEP990101',
                          'DESM900102',
                          'ROBB760111',
                          'TANS770105',
                          'WOLS870103',
                          'ROBB790101',
                          'GUYH850103',
                          'QIAN880124',
                          'JANJ790102',
                          'JANJ780102',
                          'WERD780101',
                          'BIOV880102',
                          'QIAN880125',
                          'WOLS870102',
                          'HOPT810101',
                          'RICJ880111']

}
