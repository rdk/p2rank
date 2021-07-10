import cz.siret.prank.program.params.Params

(params as Params).with {

    /**
     * define this if you want dataset program parameters to be evaluated relative to this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */

    //dataset_base_dir = "../../dna_datasets/"

    /**
     * all output of the program will be stored in subdirectories of this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */

    //output_base_dir = "../../dna_datasets/"

    predict_residues = true
    ligand_derived_point_labeling = false

    visualizations = false

    fail_fast = true
    threads = Runtime.getRuntime().availableProcessors() / 2;

    vis_generate_proteins = false

    log_to_file = true
    delete_models = false


    classifier="FasterForest"
    rf_trees = 10
    rf_bagsize = 50
    rf_depth = 10

    cache_datasets = false
    clear_sec_caches = false
    clear_prim_caches = false


    residue_table_features = ["RAx"]
    atom_table_features = ["atomicHydrophobicity"]
    features = ["chem","volsite","bfactor","protrusion","pmass","cr1pos","ss_atomic","ss_sas","ss_cloud"]

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


    stats_collect_predictions = true // to enable AUC and AUPRC metrics

}
