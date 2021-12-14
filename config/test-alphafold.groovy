import cz.siret.prank.program.params.Params

/*
 * P2Rank configuration for use with AlphaFold DB models (also NMR and Cryo-EM structures).
 * Same as default.groovy, but doesn't use "bfactor" as a feature.
 */
(params as Params).with {

    dataset_base_dir = "../../p2rank-datasets"

    output_base_dir = "../../p2rank-results/${version}"

    visualizations = false

    fail_fast = true

    log_to_console = false

//===========================================================================================================//

    model = "alphafold.model"

    features = ["chem","volsite","protrusion"]

    zscoretp_transformer = "alphafold_ZscoreTpTransformer.json"

    probatp_transformer = "alphafold_ProbabilityScoreTransformer.json"

    zscoretp_res_transformer = "residue/alphafold_ZscoreTpTransformer.json"

    probatp_res_transformer = "residue/alphafold_ProbabilityScoreTransformer.json"

}
