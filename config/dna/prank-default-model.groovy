import cz.siret.prank.program.params.Params

/**
 * Config that applies default P2Rank ligand binding site model to DNA binding residue prediction
 */
(params as Params).with {

    /**
     * define this if you want dataset program parameters to be evaluated relative to this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    dataset_base_dir = "../../p2rank-data-dna/"

    /**
     * all output of the program will be stored in subdirectories of this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    output_base_dir = "../../p2rank-results-dna/"

    predict_residues = true
    ligand_derived_point_labeling = false

    visualizations = false
    vis_generate_proteins = false

    fail_fast = true
    //threads = Runtime.getRuntime().availableProcessors() + 1;


    log_level = "WARN"
    log_to_file = true
    delete_models = true
    ploop_delete_runs = false
    stats_collect_predictions = true

    cache_datasets = true
    clear_sec_caches = false
    clear_prim_caches = false

    selected_stats = ['_blank',
                      'P',
                      'R',
                      'MCC',
                      'AUC',
                      'AUPRC',
                      'F1',
                      'TPX',
                      'point_P',
                      'point_R',
                      'point_MCC',
                      'point_AUC',
                      'point_AUPRC',
                      'point_F1',
                      'point_TPX',
                      'TIME_MINUTES']


    residue_score_extra_dist = 1.9806
    residue_score_threshold = 0.55
    
//
//    // General feature extraction
//
//    average_feat_vectors = true
//    avg_weighted = true
//    atom_table_feat_keep_sgn = true
//    solvent_radius = 1.8
//    surface_additional_cutoff = 2.2   // should be equal to (residue-labeling-threshold - solvent_radius), where residue-labeling-threshold is 4.0, 4.5, 6.0 etc.
//
//    // Training
//
//    balance_class_weights = true
//    target_class_weight_ratio = 0.2160
//    //subsample = true
//    //target_class_ratio = 1
//
//    // Prediction
//
//    residue_score_extra_dist = 1.9806
//    residue_score_threshold = 0.4857
//    residue_score_sum_to_avg = 0
//    pred_point_threshold = 0.5
//    point_score_pow = 5.2717
//
//    // Classifier
//
//    classifier="FasterForest2"
//    rf_trees = 100
//    rf_bagsize = 55
//    rf_depth = 0
//
//    // Features
//
//    residue_table_features = ["RAx"]
//    atom_table_features = ["atomicHydrophobicity"]
//    features = ["chem","volsite","bfactor","protrusion","pmass","cr1pos","ss_atomic","ss_sas","ss_cloud"]
//
//    // Feature params
//
//    neighbourhood_radius = 10
//    protrusion_radius = 13
//    feat_pmass_radius = 7
//    ss_cloud_radius = 6

}
