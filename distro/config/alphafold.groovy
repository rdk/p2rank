import cz.siret.prank.program.params.Params

/*
 * P2Rank configuration for use with AlphaFold, cryo-EM, and NMR models.
 * Same as default.groovy, but doesn't use "bfactor" as a feature.
 */
(params as Params).with {

    model = "alphafold"

    features = ["chem","volsite","protrusion"]

    zscoretp_transformer = "{models_dir}/_score_transform/alphafold_ZscoreTpTransformer.json"
    probatp_transformer = "{models_dir}/_score_transform/alphafold_ProbabilityScoreTransformer.json"
    zscoretp_res_transformer = "{models_dir}/_score_transform/residue/alphafold_ZscoreTpTransformer.json"
    probatp_res_transformer = "{models_dir}/_score_transform/residue/alphafold_ProbabilityScoreTransformer.json"

}
