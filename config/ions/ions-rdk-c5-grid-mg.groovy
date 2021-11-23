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
    solvent_radius = 1.8
    weight_function = "NEW"
    point_min_distfrom_protein = 2.3

    point_sampling_strategy = "grid"
    grid_cutoff_radius = 3
    grid_cell_edge = 1.8

    // Training

    positive_point_ligand_distance = 1.75
    neutral_points_margin = 2.5
    balance_class_weights = true
    target_class_weight_ratio = 0.12
    tesallation = 3                      // used during prediction
    train_tessellation = 3               // used during training for positive points around the ligand
    train_tessellation_negatives = 1     // used during training for negative points
    subsample = 1
    target_class_ratio = 0.11

    // Prediction

    point_score_pow = 12
    pred_point_threshold = 0.425
    pred_min_cluster_size = 1
    extended_pocket_cutoff = 1.6778

    // Residue Prediction

    predict_residues = false             // residue mode is off by default
    residue_point_score_pow = 4.0766
    residue_score_sum_to_avg = 0.05
    residue_score_threshold = 1.1379
    residue_score_extra_dist = -1.7556
    residue_score_transform = "NONE"

    // Classifier

    classifier="FasterForest"
    rf_trees = 240
    rf_bagsize = 80
    rf_depth = 16

    // Features

    features = ["chem_sas","volsite_sas","bfactor_sas","chem","volsite","bfactor","protrusion","ss_atomic","duplets_sas","triplets_sas","cres1","cr1pos","residue_table","residue_table_sas","atom_table","atom_table_sas","pyramid","exposed_dist"]
    residue_table_features = ["MGppuAt","RAx"]
    atom_table_features = ["apRawValids","apRawInvalids","atomicHydrophobicity"]
    feat_propensity_tables = "ions/MGppuAt"

    // Feature params

    neighbourhood_radius = 4
    protrusion_radius = 7.25
    feat_pmass_radius = 7 // feature not used
    ss_cloud_radius = 6   // feature not used

    // Conservation

    load_conservation = false
    conservation_dirs = ["conservation/uref50"]

    // Pre-trained model

    model="../p2rank-dev-models/ions/ions-rdk-c5-grid-mg.model"

}
