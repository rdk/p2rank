import cz.siret.prank.program.params.Params

/**
 * Config for ion binding site prediction: checkpoint 1
 */
(params as Params).with {

    /**
     * define this if you want dataset program parameters to be evaluated relative to this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    dataset_base_dir = "../../p2rank-ions-data-rdk/"

    /**
     * all output of the program will be stored in subdirectories of this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    output_base_dir = "../../p2rank-ions-results/"

    predict_residues = false

    visualizations = false
    vis_generate_proteins = false
    vis_highlight_ligands = true

    fail_fast = false
    threads = Runtime.getRuntime().availableProcessors() + 1;


    log_level = "WARN"
    log_to_file = true
    delete_models = true
    ploop_delete_runs = false
    stats_collect_predictions = true
    log_cases = true

    cache_datasets = false
    clear_sec_caches = false
    clear_prim_caches = false

    selected_stats = ['_blank',
                      'DCA_4_0',
                      'DCA_4_2',
                      'DCA_4_10',
                      'DCA_4_99',
                      'point_AUPRC',
                      'point_AUC',
                      'point_MCC',
                      'point_F1',
                      'point_TPX',
                      'TIME_MINUTES']


    // General feature extraction

    average_feat_vectors = true
    avg_weighted = true
    atom_table_feat_keep_sgn = true
    solvent_radius = 1.2781

    // Training

    positive_point_ligand_distance = 5.1286
    neutral_points_margin = 3
    balance_class_weights = true
    target_class_weight_ratio = 0.0684
    //subsample = true
    //target_class_ratio = 1

    // Prediction

    point_score_pow = 10
    pred_point_threshold = 0.5678
    pred_min_cluster_size = 1
    extended_pocket_cutoff = 1

    // Classifier

    classifier="FasterForest"
    rf_trees = 100
    rf_bagsize = 55
    rf_depth = 12

    // Features

    residue_table_features = ["RAx"]
    atom_table_features = ["atomicHydrophobicity"]
    features = ["chem","volsite","bfactor","protrusion"]

    // Feature params

    neighbourhood_radius = 5.9
    protrusion_radius = 7
    feat_pmass_radius = 7 // feature not used
    ss_cloud_radius = 6   // feature not used

}
