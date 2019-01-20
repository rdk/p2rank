import cz.siret.prank.program.params.Params

/**
 * P2Rank pocket prediction model with conservation.
 */
(params as Params).with {

    model = "conservation.model"

    extra_features = ["chem","volsite","protrusion","bfactor","conservation"]

    load_conservation = true

    load_conservation_paths = true

    conservation_origin = "hssp"
    
}