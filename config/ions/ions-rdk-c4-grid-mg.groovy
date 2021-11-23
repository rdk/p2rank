import cz.siret.prank.program.params.Params

/**
 * Config for ion binding site prediction: checkpoint 4 using full 3D grid
 *
 *
 * TODO: optimize residue mode params
 */
(params as Params).with {

    /**
     * define this if you want dataset program parameters to be evaluated relative to this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    dataset_base_dir = "../../p2rank-ions-data/"

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
    solvent_radius = 0.5

    point_sampling_strategy = "grid"
    grid_cutoff_radius = 3

    // Training

    positive_point_ligand_distance = 1.75
    neutral_points_margin = 3
    balance_class_weights = true
    target_class_weight_ratio = 0.08
    tesallation = 3                      // used during prediction
    train_tessellation = 3               // used during training for positive points around the ligand
    train_tessellation_negatives = 1     // used during training for negative points

    // Prediction

    point_score_pow = 8.2248
    pred_point_threshold = 0.5153
    pred_min_cluster_size = 1
    extended_pocket_cutoff = 1.6778

    // Residue Prediction

    predict_residues = false             // residue mode is off by default
    residue_score_sum_to_avg = 0.05
    residue_score_threshold = 0.93
    residue_score_extra_dist = -0.6
    residue_score_transform = "SIGMOID"

    // Classifier

    classifier="FasterForest"
    rf_trees = 200
    rf_bagsize = 55
    rf_depth = 12

    // Features

    features = ["chem","volsite","bfactor","protrusion","conserv_atomic","ss_atomic","duplets_sas","triplets_sas"]
    residue_table_features = ["MGppuAt"]
    atom_table_features = ["atomicHydrophobicity"]
    feat_propensity_tables = "ions/MGppuAt"

    // Feature params

    neighbourhood_radius = 5
    protrusion_radius = 11.3
    feat_pmass_radius = 7 // feature not used
    ss_cloud_radius = 6   // feature not used

    // Conservation

    load_conservation = true
    conservation_dirs = ["conservation/uref50"]

    // Pre-trained model

    model="../p2rank-dev-models/ions/ions-rdk-c4-grid-mg.model"

}
