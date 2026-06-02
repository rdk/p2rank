import cz.siret.prank.program.params.Params

/**
 * physics-rich-v2-train: train a single, KEPT model with the physics-rich-v2 feature set.
 *
 * Unlike physics-rich-v2.groovy (a ploop eval that deletes its models), this
 * config trains one deployable FasterForest model and leaves it on disk under
 * local/ so it can be inspected or used with `prank predict -m <model>`.
 *
 * Feature set: the physics-rich-v2 features WITHOUT conservation -- the eval's
 * winning graph-centrality pair (cg_closeness_sas + cg_betweenness_atomic) +
 * electrostatics + e3 probes, on the plain chem/volsite/protrusion/bfactor
 * baseline. Conservation is intentionally excluded (no HMM .hom dependency;
 * isolates the physics-feature contribution -- cf. the eval's "no conservation"
 * tables, where the physics pair shows its largest gain, +0.037 AUPRC).
 * See local/reports/2026-05-24_physics_features_eval.md.
 *
 * Usage (from repo root):
 *   ./prank.sh traineval -t <train.ds> -e <eval.ds> -c config/physics-rich-v2-train.groovy
 *   # or, to train on the whole dataset without an eval split:
 *   ./prank.sh train -t <train.ds> -c config/physics-rich-v2-train.groovy
 *
 * The model lands in:
 *   local/trained-models/<version>/<run-dir>/runs/seed.42/FasterForest.model
 * (paths resolve relative to distro/, so "../local" == p2rank-private/local).
 *
 * Once trained, point a model-linked "final" predict config at it. Those live
 * in local/ next to the model (gitignored), e.g.
 *   local/trained-models/physics-rich-v2.final.groovy
 * Generic feature configs (this file) stay in config/; model-linked ones do not.
 */
(params as Params).with {

    dataset_base_dir = "../../p2rank-datasets2"

    // tidy, gitignored home for kept models (NOT the shared results dir)
    output_base_dir = "../local/trained-models/${version}"

    visualizations = false

    // --- the whole point: keep the trained model ---
    delete_models = false
    delete_vectors = true   // feature vectors are large and not needed post-train

    max_train_instances = 0

    fail_fast = false

    seed = 42

    loop = 1

    out_prefix_date = false

    crossval_threads = 1

    cache_datasets = true

    clear_prim_caches = false

    clear_sec_caches = false

    // dump feature importances alongside the model (cheap, handy for inspection)
    feature_importances = true

    // keep full run output (incl. the model), not just the stats summary
    output_only_stats = false

    log_cases = true

    log_to_console = false

    log_level = "WARN"

    log_to_file = true

    // not a ploop run: do not delete/zip run dirs (that would take the model with them)
    ploop_delete_runs = false

    ploop_zip_runs = false

    zip_log_file = true

//===========================================================================================================//

    sample_negatives_from_decoys = false

    atom_table_feat_keep_sgn = false

//===========================================================================================================//

    features = ["chem","volsite","protrusion","bfactor"]

    load_conservation = false

    extra_features = ["cg_closeness_sas","cg_betweenness_atomic","electrostatics",
                       "e3-neutral-apolar","e3-hb-donor","e3-hb-acceptor","e3-cation","e3-aromatic-ring"]

    classifier = "FasterForest"

    rf_trees = 200

    // fit pocket score-transformers during the eval phase so predictions are
    // calibrated (zScoreTP + probaTP). Written to <run-outdir>/score/<name>.json;
    // the wrapper wires them into each variant's final predict config.
    train_score_transformers = ["ZscoreTpTransformer","ProbabilityScoreTransformer"]

}
