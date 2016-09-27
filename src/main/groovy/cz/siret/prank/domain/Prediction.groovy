package cz.siret.prank.domain

import groovy.util.logging.Slf4j

/**
 * Pocket prediction result for single protein.
 */
@Slf4j
class Prediction {

    Protein protein
    List<Pocket> pockets
    List<Pocket> reorderedPockets

    Prediction(Protein protein, List<Pocket> pockets) {
        this.protein = protein
        this.pockets = pockets
    }

    int getPocketCount() {
        return pockets.size()
    }

}
