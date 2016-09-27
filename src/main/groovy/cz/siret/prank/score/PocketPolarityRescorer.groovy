package cz.siret.prank.score

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction

class PocketPolarityRescorer extends PocketRescorer {

    @Override
    void rescorePockets(Prediction prediction) {
        prediction.pockets.each { Pocket pocket ->

            pocket.newScore = pocket.stats.polarityScore
        }
    }

}
