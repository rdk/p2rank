package cz.siret.prank.collectors

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.WekaUtils
import weka.core.Instances

@Slf4j
@CompileStatic
class DataPreProcessor implements Parametrized {

    Instances preProcessTrainData(Instances data) {
        double removePercentage = 0
        int reduceToSubsetSize = params.max_train_instances

        // reduce size
        if (reduceToSubsetSize>0 && data.size() >reduceToSubsetSize) {
            log.info "reducing vectors to subset of size $reduceToSubsetSize"
            removePercentage = 100 * (1 - (double)reduceToSubsetSize/data.size())
        }
        if (removePercentage>0.0) {
            log.info "removing percentage $removePercentage"
            data = WekaUtils.randomize(params.seed, data)
            data = WekaUtils.removePercentage(removePercentage, data)
            log.info "instances left: " + data.size()
        }

        return data
    }

}
