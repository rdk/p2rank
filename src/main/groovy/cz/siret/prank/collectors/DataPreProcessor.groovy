package cz.siret.prank.collectors

import cz.siret.prank.features.implementation.ProtrusionFeature
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.PerfUtils
import cz.siret.prank.utils.WekaUtils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import weka.core.Attribute
import weka.core.Instances

@Slf4j
@CompileStatic
class DataPreProcessor implements Parametrized, Writable {

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
            data = WekaUtils.randomSubsample(1-removePercentage, params.seed, data)
            log.info "instances left: " + data.size()
        }



        if (params.subsample) {

            def split = WekaUtils.splitPositivesNegatives(data)
            Instances positives = split[0]
            Instances negatives = split[1]

            int pc = positives.size()
            int nc = negatives.size()

            double ratio = (double)pc / nc
            double targetRatio = params.train_class_ratio

            int seed = new Random().nextInt()

            write "targetRatio: $targetRatio"

            if (ratio < targetRatio) {
                write "subsampling negatives (${descState(positives, negatives)})"
                double keepPercent = ratio / targetRatio
                write "keepPc: $keepPercent"

                if (params.subsampl_high_protrusion_negatives) {
                    // sory by protrusion desc before subsampling
                    Attribute attr = negatives.attribute(ProtrusionFeature.NAME)
                    if (attr!=null) {
                        negatives.sort(attr)
                        //negatives = WekaUtils.reverse(negatives)
                    }
                    negatives = WekaUtils.removeRatio(negatives, 1d-keepPercent)
                } else {
                    // random subsampling
                    negatives = WekaUtils.randomSubsample(keepPercent, seed, negatives)
                }


                write "negatives subsampled (${descState(positives, negatives)})"
            } else {
                write "subsampling positives (${descState(positives, negatives)})"
                double keepPercent = targetRatio / ratio
                write "keepPc: $keepPercent"
                positives = WekaUtils.randomSubsample(keepPercent, seed, positives)
                write "positives subsampled (${descState(positives, negatives)})"
            }

            data = WekaUtils.joinInstances([positives, negatives])
            data = WekaUtils.randomize(data, seed)

        }

        return data
    }

    private descState(Instances positives, Instances negatives) {
        int pos = positives.size()
        int neg = negatives.size()

        double ratio = PerfUtils.round( (double)pos / neg, 3 )
        "positives: $pos, negatives: $neg, ratio:$ratio"
    }

}
