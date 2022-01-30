import cz.siret.prank.program.params.Params

/*
 * P2Rank configuration for use with AlphaFold, cryo-EM, and NMR models.
 * Same as conservation_hmm.groovy, but doesn't use "bfactor" as a feature.
 */
(params as Params).with {

    model = "alphafold_conservation_hmm.model"

    features = ["chem","volsite","protrusion","conservation"]

    load_conservation = true

    zscoretp_transformer = "alphafold_conservation_hmm_ZscoreTpTransformer.json"

    probatp_transformer = "alphafold_conservation_hmm_ProbabilityScoreTransformer.json"

    zscoretp_res_transformer = "residue/alphafold_conservation_hmm_ZscoreTpTransformer.json"

    probatp_res_transformer = "residue/alphafold_conservation_hmm_ProbabilityScoreTransformer.json"

}
