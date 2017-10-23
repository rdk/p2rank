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


    /**
     * collect negatives just from decoy pockets found by other method
     * (alternatively take negative points from all of the protein's surface)
     */
    sample_negatives_from_decoys = false

    atom_table_feat_keep_sgn = true

    atom_table_features = ["ap5sasaValids","ap5sasaInvalids","apRawValids","apRawInvalids","atomicHydrophobicity"]

    extra_features = ["chem","volsite","protrusion","bfactor"]

    residue_table_features = ["RAx"]

    average_feat_vectors = true

    balance_class_weights = true

    target_class_weight_ratio = 0.055
    

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

    feat_aa_properties = ['KOEP990101',
                          'WERD780101',
                          'CASG920101',
                          'RACS820107',
                          'BAEK050101',
                          'TANS770105',
                          'SUYM030101',
                          'DESM900101',
                          'GUYH850102',
                          'FASG890101',
                          'MIYS990105',
                          'BASU050102',
                          'JANJ780102',
                          'DESM900102',
                          'RICJ880115',
                          'GUYH850103',
                          'RADA880108',
                          'ROBB790101',
                          'CIDH920101',
                          'ROBB760111',
                          'BIOV880101',
                          'JANJ790102',
                          'NISK860101',
                          'QIAN880122',
                          'MIYS990104',
                          'FASG760103',
                          'HOPT810101',
                          'RACS820106',
                          'WOLS870102',
                          'BIOV880102',
                          'PUNT030101',
                          'NISK800101',
                          'QIAN880124',
                          'QIAN880137',
                          'AURR980117',
                          'BASU050103',
                          'KHAG800101',
                          'MAXF760104',
                          'WOLS870103',
                          'AVBF000106',
                          'FAUJ830101',
                          'OOBM770103',
                          'TANS770109',
                          'PALJ810111',
                          'PONP930101',
                          'CHOC760104',
                          'CIDH920102',
                          'CORJ870106',
                          'FUKS010101',
                          'MEEJ810101',
                          'TANS770107',
                          'GUYH850101',
                          'ISOY800108',
                          'JANJ790101',
                          'RACS820112',
                          'AVBF000105',
                          'CORJ870105',
                          'ISOY800105',
                          'KANM800102',
                          'MAXF760105',
                          'QIAN880125',
                          'RACS820113',
                          'SNEP660103',
                          'CHOP780202',
                          'FODM020101',
                          'MIYS850101',
                          'FUKS010104',
                          'NADH010104',
                          'PALJ810103',
                          'PONP800107',
                          'RICJ880101',
                          'AVBF000103',
                          'KIDA850101',
                          'QIAN880130',
                          'FUKS010102',
                          'LEVM760101',
                          'NAGK730102',
                          'PRAM820102',
                          'RICJ880111',
                          'CHOP780212',
                          'CHOP780214',
                          'PONP800101',
                          'ROSM880105',
                          'WERD780104',
                          'CHOC760103',
                          'FINA910104',
                          'QIAN880105',
                          'QIAN880123',
                          'QIAN880136',
                          'ZHOH040103']
}
