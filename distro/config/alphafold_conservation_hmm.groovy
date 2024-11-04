import cz.siret.prank.program.params.Params

/*
 * P2Rank configuration for use with AlphaFold, cryo-EM, and NMR models.
 * Same as conservation_hmm.groovy, but doesn't use "bfactor" as a feature.
 */
(params as Params).with {

    model = "alphafold_conservation_hmm"

    features = ["chem","volsite","protrusion","conservation"]

    load_conservation = true

    zscoretp_transformer = "{models_dir}/_score_transform/alphafold_conservation_hmm_ZscoreTpTransformer.json"
    probatp_transformer = "{models_dir}/_score_transform/alphafold_conservation_hmm_ProbabilityScoreTransformer.json"
    zscoretp_res_transformer = "{models_dir}/_score_transform/residue/alphafold_conservation_hmm_ZscoreTpTransformer.json"
    probatp_res_transformer = "{models_dir}/_score_transform/residue/alphafold_conservation_hmm_ProbabilityScoreTransformer.json"

}
