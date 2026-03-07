import cz.siret.prank.program.params.Params

/**
 *  For development.
 *
 *  Config that is mostly the same as the default config in distro/config/default.groovy,
 *  just some technical parameters are changed. Used for running experiments during development.
 */
(params as Params).with {

    dataset_base_dir = "../../p2rank-datasets"

    output_base_dir = "../../p2rank-results/${version}"

    visualizations = false

    fail_fast = false

    log_to_console = true

    rf_flatten = false

    rf_batch_prediction = true

}
