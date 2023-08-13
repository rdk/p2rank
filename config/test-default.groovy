import cz.siret.prank.program.params.Params

/**
 *  Allows testing already trained default model.
 *
 *  Config that is mostly the same as the default config in distro/config/default.groovy,
 *  just some technical parameters are changed. Used for running tests and speed benchmarking.
 */
(params as Params).with {

    dataset_base_dir = "../../p2rank-datasets"

    output_base_dir = "../../p2rank-results/${version}"

    visualizations = false

    fail_fast = true

    log_to_console = false

    rf_flatten = true

}
