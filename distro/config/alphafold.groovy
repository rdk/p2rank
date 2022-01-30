import cz.siret.prank.program.params.Params

/*
 * P2Rank configuration for use with AlphaFold, cryo-EM, and NMR models.
 * Same as default.groovy, but doesn't use "bfactor" as a feature.
 */
(params as Params).with {

    model = "alphafold.model"

    features = ["chem","volsite","protrusion"]

    zscoretp_transformer = "alphafold_ZscoreTpTransformer.json"

    probatp_transformer = "alphafold_ProbabilityScoreTransformer.json"

    zscoretp_res_transformer = "residue/alphafold_ZscoreTpTransformer.json"

    probatp_res_transformer = "residue/alphafold_ProbabilityScoreTransformer.json"

}
