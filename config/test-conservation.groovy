import cz.siret.prank.program.params.Params

/**
 *  Allows testing already trained conservation model.
 *
 *  Config that is mostly the same as the default config in distro/config/conservation.groovy,
 *  just some technical parameters are changed. Used for running tests and speed benchmarking.
 */
(params as Params).with {

    dataset_base_dir = "../../p2rank-datasets"

    output_base_dir = "../../p2rank-results/${version}"

    visualizations = false

    /**
     * Here must be false for testing. Because if true,
     * fails on every conservation score file that is missing (it's calculation failed)
     * ...and there are some for each dataset.
     */
    fail_fast = false

    log_to_console = false

//===========================================================================================================//

    model = "conservation.model"

    features = ["chem","volsite","protrusion","bfactor","conservation"]

    load_conservation = true

    classifier = "FasterForest"
    
}