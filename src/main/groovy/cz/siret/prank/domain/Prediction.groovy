package cz.siret.prank.domain

import cz.siret.prank.program.rendering.LabeledPoint
import groovy.util.logging.Slf4j

/**
 * Pocket prediction result for single protein.
 *
 *
 */
@Slf4j
class Prediction {

    Protein protein

    /**
     * pockets predicted by P2RANK or other prediction method
     */
    List<Pocket> pockets

    /**
     * reordered pockets (relevant only when doing rescoring with old PRANK algorihhm)
     */
    List<Pocket> reorderedPockets

    /**
     *  Connolly points with ligandability score for prediction and visualization.
     */
    List<LabeledPoint> labeledPoints = null


    Prediction(Protein protein, List<Pocket> pockets) {
        this.protein = protein
        this.pockets = pockets
    }

    int getPocketCount() {
        return pockets.size()
    }

}
