import cz.siret.prank.program.params.Params

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

    fail_fast = true
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
    solvent_radius = 1.8

    // Training

    positive_point_ligand_distance = 4.6
    neutral_points_margin = 5.5
    balance_class_weights = true
    target_class_weight_ratio = 0.08
    //subsample = true
    //target_class_ratio = 1

    // Prediction

    point_score_pow = 5.2717
    pred_point_threshold = 0.5
    pred_min_cluster_size = 3

    // Classifier

    classifier="FasterForest"
    rf_trees = 100
    rf_bagsize = 55
    rf_depth = 0

    // Features

    residue_table_features = ["RAx"]
    atom_table_features = ["atomicHydrophobicity"]
    // atom_table_features = ["atomicHydrophobicity","ap5sasaValids","ap5sasaInvalids","apRawValids","apRawInvalids"]
    features = ["chem","volsite","bfactor","protrusion"]
    // features = ["chem","volsite","bfactor","protrusion","pmass","cr1pos","ss_atomic","ss_sas","ss_cloud"]

    // Feature params

    neighbourhood_radius = 7
    protrusion_radius = 11
    feat_pmass_radius = 7
    ss_cloud_radius = 6

}
