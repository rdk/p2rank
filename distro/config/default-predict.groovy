import cz.siret.prank.features.weight.WeightFun
import cz.siret.prank.program.params.Params

(params as Params).with {

    /**
     * Number of computing threads
     */
    threads = Runtime.getRuntime().availableProcessors() + 1

    /**
     * define this if you want dataset program parameters to be evaluated relative to this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    dataset_base_dir = "./test_data"

    /**
     * all output of the prorgam will be stored in subdirectores of this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    output_base_dir = "./test_output"

    /**
     * default model
     * (set path relative to install_dir/models/)
     */
    model = "p2rank_a.model"


    /**
     * produce pymol visualisations
     */
    visualizations = true

    /**
     * copy all protein pdb files to visualization folder (making visualizations portable)
     */
    vis_copy_proteins = true

    /**
     * stop processing a datsaset on the first unrecoverable error with a dataset item
     */
    fail_fast = false

    delete_models = true

    delete_vectors = true

    cache_datasets = false

    predictions = true

    out_prefix_date = false

    log_cases = false

    max_train_instances = 0


    classifier="FastRandomForest"

    rf_trees = 100

    seed = 42

    loop = 10

    use_volsite_features = true

    atom_table_features = ["apRawValids","apRawInvalids","atomicHydrophobicity"]

    extra_features = ["protrusion","bfactor"]

    residue_table_features = []

    protrusion_radius = 10

    weight_function = WeightFun.Option.INV

    // optimized values

    pred_point_threshold = 0.35

    pred_clustering_dist = 3

    neighbourhood_radius = 6

    positive_point_ligand_distance = 2.6

    train_pockets = 9

    surface_additional_cutoff = 2.5

}
