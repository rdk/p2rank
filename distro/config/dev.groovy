import cz.siret.prank.program.params.Params

/**
 * Parameters useful in the training phase.
 */
(params as Params).with {

    /**
     * define this if you want dataset program parameters to be evaluated relative to this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    dataset_base_dir = "../../pocket-rank-data/datasets"

    /**
     * all output of the program will be stored in subdirectories of this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    output_base_dir = "../../pocket-rank-results"

    /**
     * produce pymol visualisations
     */
    visualizations = false


    /**
     * stop processing a dataset on the first unrecoverable error with a dataset item
     */
    fail_fast = true


    delete_models = false

    delete_vectors = false

    /**
     * keep datasets (structures and Connolly points) in memory between crossval/seedloop iterations
     */
    cache_datasets = true

    log_cases = true


}
