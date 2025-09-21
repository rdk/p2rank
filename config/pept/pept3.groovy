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
    dataset_base_dir = "../../../p2rank-pept-data/peptides/sprint17"

    /**
     * all output of the program will be stored in subdirectories of this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    output_base_dir = "../../../p2rank-pept-results/${version}"


    predict_residues = true

    visualizations = false

    fail_fast = true

    vis_generate_proteins = true

    log_to_file = false

    //

    classifier = "FasterForest2"
    rf_trees = 400
    rf_bagsize = 55
    rf_features = 15
    rf_flatten = true
    rf_flatten_as_legacy = true


    tessellation = 3
    train_tessellation = 3
    train_tessellation_negatives = 2

    atom_table_feat_keep_sgn = true
    residue_table_features = ["RAx"]
    atom_table_features = [""]
    features = [
        "residue_table",
        "chem",
        "sss_atomic",
        "protr_hist",
        "contactres_sas",
        "asa",
        "volsite",
        "atomtype-propensity",
        "sidechain_cloud",
        "surface_protrusion",
        "protrusion",
        "pmass",
        "sidechain",
        "sss_motif_atomic",
        "ss_cloud",
        "volsite_sas",
        "conserv_atomic",
        "conserv_cloud",
        "z-conserv_atomic",
        "z-conserv_cloud2"
    ]

    load_conservation = 1
    conservation_dir = 'conservation/hmm/scores'
    conserv_cloud_radius = 13
    conservation_exponent = 1

    balance_class_weights = true
    target_class_weight_ratio = 0.2160
    subsample = true
    target_class_ratio = 0.25

    residue_score_extra_dist = 1.22
    residue_score_threshold = 0.6
    residue_score_sum_to_avg = 0
    pred_point_threshold = 0.5
    point_score_pow = 3.75

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



//./prank.sh traineval -t train.ds -e test.ds -out_subdir P25_2 -l R2_checkpoint2 \
//    -c config/pept/pept1 \
//    -average_feat_vectors 1 \
//    -subsample 1 \
//    -balance_class_weights 1 \
//    -pred_point_threshold 0.5 \
//    -feat_propensity_tables peptides/SprintT1070 \
//    -residue_table_features '(RAx)' \
//    -features '(residue_table,chem,sss_atomic,protr_hist,contactres_sas,asa,volsite,atomtype-propensity,sidechain_cloud,surface_protrusion,protrusion,pmass,sidechain,sss_motif_atomic,ss_cloud,volsite_sas,conserv_atomic,conserv_cloud,z-conserv_atomic,z-conserv_cloud2)' \
//    -extra_features '()' \
//    -atom_table_features '()' \
//    -feat_stmotif_motifs '(WR,RD,RE,FER,KE,KD,FEK,WK,FF)' \
//    -load_conservation 1 \
//    -conservation_dirs 'conservation/hmm/scores' \
//    -target_class_weight_ratio 0.2160 \
//    -point_score_pow 3.75 \
//    -residue_score_threshold 0.6 \
//    -residue_score_extra_dist 1.22 \
//    -residue_score_sum_to_avg 0 \
//    -score_point_limit 0 \
//    -target_class_ratio 0.25 \
//    -neighbourhood_radius 10 \
//    -protrusion_radius 13 \
//    -feat_pmass_radius 7 \
//    -ss_cloud_radius   6 \
//    -conserv_cloud_radius 13 \
//    -conservation_exponent 1 \
//    -classifier FasterForest2 \
//    -rf_trees 400 \
//    -rf_bagsize 55 \
//    -visualizations 0 \
//    -feature_importances 0 \
//    -stats_collect_predictions 1 \
//    -log_cases 0 \
//    -ploop_delete_runs 1 \
//    -identify_peptides_by_labeling 1 \
//    -cache_datasets 1 \
//    -clear_prim_caches 0 \
//    -clear_sec_caches 0 \
//    -hopt_train_only_once 1 \
//    -collect_only_once 0 \
//    -hopt_cache_labeled_points 0 \
//    -fail_fast 0 \
//    -train_tessellation_negatives 2 \
//    -train_tessellation 3 \
//    -tessellation 3 \
//    -loop 5
//
//    pt_AUC:   0.8214 | res_AUC:   0.8696
//    pt_AUPRC: 0.3043 | res_AUPRC: 0.357
//    pt_P:     0.4821 | res_P:     0.185
//    pt_R:     0.2128 | res_R:     0.7858
//    pt_MCC:   0.2999 | res_MCC:   0.3141
//    pt_TPX:   0.1732 | res_TPX:   0.1761



}
