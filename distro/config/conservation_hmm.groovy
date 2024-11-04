import cz.siret.prank.program.params.Params

/**
 * P2Rank configuration for use with the new, HMMER-based conservation pipeline.
 */
(params as Params).with {

    model = "conservation_hmm"

    features = ["chem","volsite","protrusion","bfactor","conservation"]

    load_conservation = true

    zscoretp_transformer = "{models_dir}/_score_transform/conservation_hmm_ZscoreTpTransformer.json"
    probatp_transformer = "{models_dir}/_score_transform/conservation_hmm_ProbabilityScoreTransformer.json"
    zscoretp_res_transformer = "{models_dir}/_score_transform/residue/conservation_hmm_ZscoreTpTransformer.json"
    probatp_res_transformer = "{models_dir}/_score_transform/residue/conservation_hmm_ProbabilityScoreTransformer.json"

}
