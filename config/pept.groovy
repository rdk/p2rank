import cz.siret.prank.program.params.Params

/**
 *  config for peptide binding residue prediction
 */
(params as Params).with {

    /**
     * define this if you want dataset program parameters to be evaluated relative to this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    dataset_base_dir = "../../p2rank-datasets2/peptides/sprint17"

    /**
     * all output of the prorgam will be stored in subdirectores of this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    output_base_dir = "../../p2rank-results-pept/${version}"


    predict_residues = true

    visualizations = false

    fail_fast = true

    load_only_specified_chains = true

    vis_generate_proteins = true

    log_to_file = false

    //

    residue_score_threshold = 2.45d

    //

    classifier="FasterForest"

    atom_table_feat_keep_sgn = true

    atom_table_features = ["ap5sasaValids","ap5sasaInvalids","apRawValids","apRawInvalids","atomicHydrophobicity"]

    extra_features = ["chem","volsite","protrusion","bfactor"]

    residue_table_features = ["RAx"]

    avg_weighted = true

    average_feat_vectors = true

    balance_class_weights = true

    target_class_weight_ratio = 0.053

    solvent_radius = 1.8

    identify_peptides_by_labeling = false

    neutral_points_margin = 0

    sample_negatives_from_decoys = false

    // technical

    ploop_delete_runs = true

    cache_datasets = true

    clear_sec_caches = false

    clear_prim_caches = false

    log_level = "WARN"

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


}
