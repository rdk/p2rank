import cz.siret.prank.program.params.Params

/**
 * physics-rich-v2: conservation + per-chain graph centrality + electrostatics + e3 direct probes.
 *
 * Extends train-conservation with physics-based features that generalize
 * across coach420, holo4k, and joined datasets without DCA regression.
 */
(params as Params).with {

    dataset_base_dir = "../../p2rank-datasets2"

    output_base_dir = "../../p2rank-results/${version}"

    visualizations = false

    delete_models = true

    delete_vectors = true

    max_train_instances = 0

    fail_fast = false

    seed = 42

    loop = 1

    out_prefix_date = false

    crossval_threads = 1

    cache_datasets = true

    clear_prim_caches = false

    clear_sec_caches = false

    feature_importances = false

    output_only_stats = true

    log_cases = true

    log_to_console = false

    log_level = "WARN"

    log_to_file = true

    ploop_delete_runs = true

    ploop_zip_runs = true

    zip_log_file = true

//===========================================================================================================//

    sample_negatives_from_decoys = false

    atom_table_feat_keep_sgn = false

//===========================================================================================================//

    features = ["chem","volsite","protrusion","bfactor","conservation"]

    load_conservation = true

    extra_features = ["cg_closeness_sas","cg_betweenness_atomic","electrostatics",
                       "e3-neutral-apolar","e3-hb-donor","e3-hb-acceptor","e3-cation","e3-aromatic-ring"]

    classifier = "FasterForest"

    rf_trees = 200

}
