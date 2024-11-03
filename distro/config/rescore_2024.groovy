import cz.siret.prank.program.params.Params

/**
 * Config for a preview of the new rescoring model
 */
(params as Params).with {

    /**
     * default model
     * (set path relative to install_dir/models/)
     */
    model = "rescore_2024"

    delete_models = true

    delete_vectors = true

    /**
     * output detailed tables for all proteins, ligands and pockets
     */
    log_cases = true

    /**
     * stop processing the dataset on the first unrecoverable error with a dataset item
     */
    fail_fast = false

    /**
     * don't produce prediction files for individual proteins (useful for long repetitive experiments)
     */
    output_only_stats = false

    /**
     * number of random seed iterations
     */
    loop = 1

    /**
     * keep datasets (structures and Connolly points) in memory between crossval/seedloop iterations
     */
    cache_datasets = false

    /**
     * clear primary caches (protein structures) between runs (when iterating params or seed)
     */
    clear_prim_caches = false

    /**
     * clear secondary caches (protein surfaces etc.) between runs (when iterating params or seed)
     */
    clear_sec_caches = false

    /**
     * produce pymol visualisations
     */
    visualizations = true

    /**
     * Collect predictions for all points in the dataset.
     * Allows calculation of AUC and AUPRC classifier statistics but consumes a lot of memory.
     * (>1GB for holo4k dataset with tessellation=2)
     */
    stats_collect_predictions = true

    /**
     * produce classifier stats also for train dataset
     */
    classifier_train_stats = false

    //== FAETURES ===================

    features = ["chem","protrusion","atom_table","volsite"]

    atom_table_features = ["ap5sasaValids","ap5sasaInvalids","aph","anh"]

    atom_table_feat_pow = 4.0

    /**
     * dummy param to preserve behaviour of older versions
     */
    atom_table_feat_keep_sgn = true

    residue_table_features = []

    protrusion_radius = 10

    
    //== CLASSIFIERS ===================

    /**
     * see ClassifierOption
     */
    classifier = "FasterForest"

    /**
     * RandomForest trees
     */
    rf_trees = 120

    /**
     * RandomForest depth limit, 0=unlimited
     */
    rf_depth = 0

    /**
     * RandomForest feature subset size for one tree, 0=default(sqrt)
     */
    rf_features = 0

    /**
     * number of threads used in RandomForest training (0=use value of threads param)
     */
    rf_threads = 0


    //=== Distances and thresholds =================

    /**
     * n, use only top-n pockets to select training instances, 0=all
     */
    train_pockets = 0

    /**
     * cutoff around ligand that defines positives
     */
    positive_point_ligand_distance = 2.5

    /**
     * points between [positive_point_ligand_distance,neutral_point_margin] will be left out form training
     */
    neutral_points_margin = 5.5

    /**
     * chem. properties representation neighbourhood radius in A
     */
    neighbourhood_radius = 8


    /**
     * multiplier for random point sub/super-sampling
     */
    sampling_multiplier = 3

    /**
     * solvent radius for Connolly surface
     */
    solvent_radius = 1.6

    /**
     * SAS tessellation (~density) used in prediction step.
     * Higher tessellation = higher density (+1 ~~ x4 points)
     */
    tessellation = 3

    /**
     * SAS tessellation (~density) used in training step
     * 0 = use value of tessellation
     */
    train_tessellation = 3

    /**
     * SAS tessellation (~density) used in training step to select negatives.
     * Allows denser positive sampling than negative sampling and thus deal with class imbalance and train faster.
     * 0 = use value of effective train_tessellation
     */
    train_tessellation_negatives = 3


    weight_power = 2
    weight_sigma = 2.2
    weight_dist_param = 4.5

    weight_function = "INV"


    average_feat_vectors = false

    avg_pow = 1

    point_score_pow = 2

    /**
     * use strictly inner pocket points or more wider pocket neighbourhood
     */
    strict_inner_points = false

    /**
     * collect evaluations for top [n+0, n+1,...] pockets (n is true pocket count)
     */
    eval_tolerances = [0,1,2,4,10,99]

    /**
     * make own prank pocket predictions (P2RANK)
     */
    predictions = false

    /**
     * minimum ligandability score for SAS point to be considered ligandable
     */
    pred_point_threshold = 0.4

    /**
     * cutoff for protein exposed atoms calculation (distance from connolly surface is solv.radius. + surf_cutoff)
     */
    surface_additional_cutoff = 1.8

    /**
     * collect negatives just from decoy pockets found by other method
     * (alternatively take negative points from all of the protein's surface)
     */
    sample_negatives_from_decoys = true


    /**
     * Path to a JSON file that contains parameters of a transformer from raw score to "z-score calculated from the distribution of true pockets" (pocket.auxInfo.zScoreTP).
     * {models_dir} resolves the directory with models "{install_dir}/models". {model} resolves to the directory of the current model.
     */
    zscoretp_transformer = "{model}/score_transform/pocket/ZscoreTpTransformer.json"

    /**
     * Path to a JSON file that contains parameters of a transformer from raw score to "probability that pocket with a given score is true pocket" (pocket.auxInfo.probaTP).
     * {models_dir} resolves the directory with models "{install_dir}/models". {model} resolves to the directory of the current model.
     */
    probatp_transformer = "{model}/score_transform/pocket/ProbabilityScoreTransformer.json"

    /**
     * Path to a JSON file that contains parameters of a transformer from raw score to "z-score calculated from the distribution of all residue scores".
     * {models_dir} resolves the directory with models "{install_dir}/models". {model} resolves to the directory of the current model.
     */
    zscoretp_res_transformer = null

    /**
     * Path to a JSON file that contains parameters of a transformer from raw score to "probability that residue with a given score is true (binding) residue".
     * {models_dir} resolves the directory with models "{install_dir}/models". {model} resolves to the directory of the current model.
     */
    probatp_res_transformer = null

}
