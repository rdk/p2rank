import cz.siret.prank.program.params.Params

/**
 *  config for peptide binding residue prediction
 *
 *  final version for conference paper  (also labeled as P1_3)
 */
(params as Params).with {

    /**
     * define this if you want dataset program parameters to be evaluated relative to this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    dataset_base_dir = "../../p2rank-pept-data/peptides/sprint17"

    /**
     * all output of the program will be stored in subdirectories of this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    output_base_dir = "../../p2rank-pept-results/${version}"


    predict_residues = true

    visualizations = false

    fail_fast = true

    vis_generate_proteins = true

    log_to_file = false

    //

    classifier="FasterForest"
    rf_trees = 500
    rf_bagsize = 55

    atom_table_feat_keep_sgn = true
    residue_table_features = ["RAx"]
    atom_table_features = ["atomicHydrophobicity"]
    features = ["chem","volsite","bfactor","protrusion","pmass","cr1pos","ss_atomic","ss_sas","ss_cloud","conserv_cloud","conserv_atomic","conserv_sas"]

    load_conservation = 1
    conservation_dir = 'conservation/train_test/e5i1'
    conserv_cloud_radius = 13
    conservation_exponent = 1

    balance_class_weights = true
    target_class_weight_ratio = 0.2160
    subsample = true
    target_class_ratio = 1

    residue_score_extra_dist = 1.9806
    residue_score_threshold = 0.4857
    residue_score_sum_to_avg = 0
    pred_point_threshold = 0.5
    point_score_pow = 5.2717

    average_feat_vectors = true
    avg_weighted = true

    neighbourhood_radius = 10
    protrusion_radius = 13
    feat_pmass_radius = 7
    ss_cloud_radius = 6

    identify_peptides_by_labeling = true

    //

    solvent_radius = 1.8
    neutral_points_margin = 0
    sample_negatives_from_decoys = false

    // technical

    ploop_delete_runs = true

    cache_datasets = true

    clear_sec_caches = false

    clear_prim_caches = false

    log_level = "WARN"

    selected_stats = ['_blank',
                      'point_MCC',
                      'point_F1',
                      'point_AUC',
                      'point_AUPRC',
                      'point_TPX',
                      'residue_MCC',
                      'residue_F1',
                      'residue_TPX',
                      'residue_AUC',
                      'residue_AUPRC',
                      'TIME_MINUTES']


}
