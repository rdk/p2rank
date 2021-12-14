import cz.siret.prank.program.params.Params

/**
 * P2Rank pocket prediction model using conservation (old pipeline).
 */
(params as Params).with {

    model = "conservation.model"

    features = ["chem","volsite","protrusion","bfactor","conservation"]

    load_conservation = true

    classifier = "FasterForest"
    
}