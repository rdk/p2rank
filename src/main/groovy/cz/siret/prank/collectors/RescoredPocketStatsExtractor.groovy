package cz.siret.prank.collectors

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.prediction.pockets.rescorers.PocketRescorer
import cz.siret.prank.prediction.pockets.criteria.PocketCriterium
import cz.siret.prank.utils.Cutils

class RescoredPocketStatsExtractor extends VectorCollector {

    PocketCriterium assessor
    PocketRescorer rescorer

    RescoredPocketStatsExtractor(PocketCriterium assessor, PocketRescorer rescorer) {
        this.assessor = assessor
        this.rescorer = rescorer
    }

    @Override
    Result collectVectors(PredictionPair pair, ProcessedItemContext context) {

        Result res = new Result()

        rescorer.rescorePockets(pair.prediction, context)

        pair.getCorrectlyPredictedPockets(assessor).each { Pocket pocket ->
            res.add( pocket.stats.getVector() + pocket.newScore + 1 )
            res.positives++
        }

        Cutils.head(3, pair.getFalsePositivePockets(assessor)).each { Pocket pocket ->
        //pair.getFalsePositivePockets(criterion).each { Pocket pocket ->
            res.add( pocket.stats.getVector() + pocket.newScore + 0  )
            res.negatives++
        }

        return res
    }

    @Override
    List<String> getHeader() {
        return Pocket.Stats.getVectorHeader()  + "new_score" + "true_pocket"
    }

}
