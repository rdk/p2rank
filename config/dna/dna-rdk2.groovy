import cz.siret.prank.program.params.Params

(params as Params).with {

    /**
     * define this if you want dataset program parameters to be evaluated relative to this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    dataset_base_dir = "../../p2rank-data-dna/"

    /**
     * all output of the program will be stored in subdirectores of this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    output_base_dir = "../../p2rank-results-dna/"

    predict_residues = true

    visualizations = false
    vis_generate_proteins = false

    fail_fast = true
    threads = Runtime.getRuntime().availableProcessors() + 1;

    load_only_specified_chains = true


    log_level = "WARN"
    log_to_file = true
    delete_models = true
    ploop_delete_runs = false
    stats_collect_predictions = true

    cache_datasets = true
    clear_sec_caches = false
    clear_prim_caches = false

    selected_stats = ['_blank',
                  'MCC',
                  'F1',
                  'AUC',
                  'AUPRC',
                  'TPX',
                  'point_MCC',
                  'point_F1',
                  'point_TPX',
                  'point_AUC',
                  'point_AUPRC',
                  'TIME_MINUTES']

    // General feature extraction 
    
    average_feat_vectors = true
    avg_weighted = true
    atom_table_feat_keep_sgn = true
    solvent_radius = 1.8   

    // Training 

    balance_class_weights = true
    target_class_weight_ratio = 0.05
    //target_class_weight_ratio = 0.2160
    //subsample = true
    //target_class_ratio = 1

    // Prediction 

    residue_score_extra_dist = 1.9806
    residue_score_threshold = 0.4857
    residue_score_sum_to_avg = 0    
    pred_point_threshold = 0.5
    point_score_pow = 5.2717       

    // Classifier
                  
    classifier="FasterForest"
    rf_trees = 100
    rf_bagsize = 55
    rf_depth = 0

    // Features

    residue_table_features = ["RAx"]
    atom_table_features = ["atomicHydrophobicity"]
    extra_features = ["chem","volsite","bfactor","protrusion","pmass","cr1pos","ss_atomic","ss_sas","ss_cloud","aa"]

    // Feature params

    neighbourhood_radius = 10
    protrusion_radius = 13
    feat_pmass_radius = 7
    ss_cloud_radius = 6

    feat_aa_properties = null





}
