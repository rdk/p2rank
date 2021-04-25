package cz.siret.prank.prediction.pockets.rescorers

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.features.api.ProcessedItemContext
import groovy.transform.CompileStatic

/**
 * keeps the original order (useful for analysis of results of methods)
 */
@CompileStatic
class IdentityRescorer extends PocketRescorer {

    @Override
    void rescorePockets(Prediction prediction, ProcessedItemContext context) {

        int invrank = prediction.pockets.size()
        prediction.pockets.each { Pocket pocket ->
            // some pocket types may not have pocket.score available, so scoring with inverse rank
            pocket.newScore = invrank--
        }
    }

}