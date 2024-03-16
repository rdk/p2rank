import cz.siret.prank.program.params.Params

(params as Params).with {

    /**
     * define this if you want dataset program parameters to be evaluated relative to this directory
     * (an absolute path or path relative to this config file dir, null defaults to the working dir)
     */
    dataset_base_dir = "../test_data"

    /**
     * all output of the program will be stored in subdirectories of this directory
     * (an absolute path or path relative to this config file dir, null defaults to the working dir)
     */
    output_base_dir = "../test_output"

    /**
     * default model
     * (set path relative to install_dir/models/)
     */
    model = "default_rescore.model"

    /**
     * Random seed
     */
    seed = 42

    parallel = true

    /**
     * Number of computing threads
     */
    threads = Runtime.getRuntime().availableProcessors() + 1

    /**
     * Number of folds to work on simultaneously
     */
    crossval_threads = 1

    /**
     * defines witch atoms around the ligand are considered to be part of the pocket
     * ligands with longer min. contact distance are considered irrelevant
     */
    ligand_protein_contact_distance = 4

    //== FAETURES

    features = ["chem","volsite","protrusion","bfactor"]

    atom_table_features = ["ap5sasaValids","ap5sasaInvalids"]

    atom_table_feat_pow = 2

    /**
     * dummy param to preserve behaviour of older versions
     */
    atom_table_feat_keep_sgn = false

    residue_table_features = []

    protrusion_radius = 10

    //== CLASSIFIERS ===================

    /**
     * see ClassifierOption
     */
    classifier = "FastRandomForest"

    meta_classifier_iterations = 5

    /**
     * works only with classifier "CostSensitive_RF"
     */
    false_positive_cost = 2

    //=== Random Forests =================

    /**
     * RandomForest trees
     */
    rf_trees = 100

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

    /**
     * cutoff for joining ligand atom groups into one ligand
     */
    ligand_clustering_distance = 1.7 // covalent bond length

    /**
     * cutoff around ligand that defines positives
     */
    positive_point_ligand_distance = 2.5

    /**
     * points between [positive_point_ligand_distance,neutral_point_margin] will be left out form training
     */
    neutral_points_margin = 5.5

    mask_unknown_residues = true

    /**
     * chem. properties representation neighbourhood radius in A
     */
    neighbourhood_radius = 8

    /**
     * HETATM groups that are considered cofactor and ignored
     */
    ignore_het_groups = ["HOH","DOD","WAT","NAG","MAN","UNK","GLC","ABA","MPD","GOL","SO4","PO4"]

    /**
     * positive podefining ligand types acceped values: "relevant", "ignored", "small", "distant"
     */
    positive_def_ligtypes = ["relevant"]

    /**
     * min. heavy atom count for ligand, other ligands ignored
     */
    min_ligand_atoms = 5

    point_sampler = "SurfacePointSampler"

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
    tessellation = 2

    /**
     * SAS tessellation (~density) used in training step
     */
    train_tessellation = 2

    // for grid and random sampling
    point_min_distfrom_protein = 2.5
    point_max_distfrom_pocket = 4.5

    /* for GridPointSampler */
    grid_cell_edge = 2

    /**
     * Restrict training set size, 0=unlimited
     */
    max_train_instances = 0

    weight_power = 2
    weight_sigma = 2.2
    weight_dist_param = 4.5

    weight_function = "INV"

    deep_surrounding = false

    /** calculate feature vectors from smooth atom feature representation
     * (instead of directly from atom properties)
     */
    smooth_representation = false

    average_feat_vectors = false

    avg_pow = 1

    point_score_pow = 2

    delete_models = false

    delete_vectors = true

    /**
     * number of random seed iterations
     */
    loop = 1

    /**
     * keep datasets (structures and Connolly points) in memory between crossval/seedloop iterations
     */
    cache_datasets = false

    /**
     * calculate feature importance
     * available only for some classifiers
     */
    feature_importances = false

    /**
     * produce pymol visualisations
     */
    visualizations = true

    /**
     * visualize all surface points (not just inner pocket points)
     */
    vis_all_surface = false

    /**
     * copy all protein pdb files to visualization folder (making visualizations portable)
     */
    vis_copy_proteins = true

    /**
     * use strictly inner pocket points or more wider pocket neighbourhood
     */
    strict_inner_points = false

    /**
     * crossvalidation folds
     */
    folds = 5

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
     * minimum cluster size (of ligandable points) for initial clustering
     */
    pred_min_cluster_size = 3

    /**
     * clustering distance for ligandable clusters for second phase clustering
     */
    pred_clustering_dist = 5

    /**
     * cutoff distance of protein surface atoms considered as part of the pocket
     */
    pred_protein_surface_cutoff = 3.5

    /**
     * Prefix output directory with date and time
     */
    out_prefix_date = false

    /**
     * Place all output files in this sub-directory of the output directory
     */
    out_subdir = null

    /**
     * Balance SAS point score weight by density (points in denser areas will have lower weight)
     */
    balance_density = false

    balance_density_radius = 2

    /**
     * output detailed tables for all proteins, ligands and pockets
     */
    log_cases = false

    /**
     * cutoff for protein exposed atoms calculation (distance from connolly surface is solv.radius. + surf_cutoff)
     */
    surface_additional_cutoff = 1.8

    /**
     * n, use only top-n pockets to select training instances, 0=all
     */
    train_pockets = 0

    /**
     * acceptable distance between ligand center and closest protein atom for relevant ligands
     */
    ligc_prot_dist = 5.5

    rescorer = "ModelBasedRescorer"

    plb_rescorer_atomic = false

    /**
     * stop processing the dataset on the first unrecoverable error with a dataset item
     */
    fail_fast = false

    /**
     * don't produce prediction files for individual proteins (useful for long repetitive experiments)
     */
    output_only_stats = false
}
