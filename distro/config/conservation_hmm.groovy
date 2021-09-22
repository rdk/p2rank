import cz.siret.prank.program.params.Params

/**
 * P2Rank configuration for use with the new, HMMER-based conservation pipeline.
 */
(params as Params).with {

    model = "conservation_hmm.model"

    features = ["chem","volsite","protrusion","bfactor","conservation"]

    load_conservation = true

}
