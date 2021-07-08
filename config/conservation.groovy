import cz.siret.prank.program.params.Params

/**
 * P2Rank pocket prediction model with conservation.
 */
(params as Params).with {

    /**
     * define this if you want dataset program parameters to be evaluated relative to this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    dataset_base_dir = "../../p2rank-datasets"

    /**
     * all output of the program will be stored in subdirectories of this directory
     * (set absolute path or path relative to install dir, null defaults to working dir)
     */
    output_base_dir = "../../p2rank-results/${version}"

    

    model = "conservation.model"

    features = ["chem","volsite","protrusion","bfactor","conservation"]

    load_conservation = true

    classifier = "FasterForest"
    
}