package cz.siret.prank.prediction.pockets.rescorers

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.features.api.ProcessedItemContext
import groovy.transform.CompileStatic

@CompileStatic
class PocketVolumeRescorer extends PocketRescorer {

    @Override
    void rescorePockets(Prediction prediction, ProcessedItemContext context) {
        prediction.pockets.each { Pocket pocket ->

            pocket.newScore = pocket.stats.realVolumeApprox
        }
    }

}