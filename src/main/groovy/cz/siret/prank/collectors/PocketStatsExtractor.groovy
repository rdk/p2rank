package cz.siret.prank.collectors

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.score.criteria.IdentificationCriterium
import cz.siret.prank.utils.ListUtils

class PocketStatsExtractor extends VectorCollector {

    IdentificationCriterium assessor

    PocketStatsExtractor(IdentificationCriterium assessor) {
        this.assessor = assessor
    }

    @Override
    Result collectVectors(PredictionPair pair) {

        Result res = new Result()

        pair.getCorrectlyPredictedPockets(assessor).each { Pocket pocket ->
            res.add( pocket.stats.getVector() + 1 )
            res.positives++
        }

        ListUtils.head(3, pair.getFalsePositivePockets(assessor)).each { Pocket pocket ->
            res.add( pocket.stats.getVector() + 0 )
            res.negatives++
        }

//        pair.prediction.pockets.each { Pocket pocket ->
//
//            if (pair.isCorrectlyPredictedPocket(pocket, criterion)) {
//                res.addAll( pocket.metrics.getVector() + 1 )
//                res.correct++
//            } else {
//                res.addAll( pocket.metrics.getVector() + 0 )
//                res.negatives++
//            }
//
//        }

        return res

    }

    @Override
    List<String> getHeader() {
        return Pocket.Stats.getVectorHeader() + "correctly_predicted"
    }

}
