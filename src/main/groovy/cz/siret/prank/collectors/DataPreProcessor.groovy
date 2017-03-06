package cz.siret.prank.collectors

import cz.siret.prank.features.implementation.ProtrusionFeature
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.PerfUtils
import cz.siret.prank.utils.WekaUtils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import weka.core.Attribute
import weka.core.Instance
import weka.core.Instances

@Slf4j
@CompileStatic
class DataPreProcessor implements Parametrized, Writable {

    Instances preProcessTrainData(Instances data) {
        double removePercentage = 0
        int reduceToSubsetSize = params.max_train_instances

        // reduce size
        if (reduceToSubsetSize>0 && data.size() > reduceToSubsetSize) {
            log.info "reducing vectors to subset of size $reduceToSubsetSize"
            removePercentage = 100 * (1 - (double)reduceToSubsetSize/data.size())
        }
        if (removePercentage > 0.0) {
            log.info "removing percentage $removePercentage"
            data = WekaUtils.randomSubsample(1-removePercentage, params.seed, data)
            log.info "instances left: " + data.size()
        }

        if (params.supersample || params.subsample) {
            data = handleClassImbalances(data)
        }

        if (params.balance_class_weights) {
            data = balanceClassWeights(data)
        }

        return data
    }


    private Instances handleClassImbalances(Instances data) {

        def split = WekaUtils.splitPositivesNegatives(data)
        Instances positives = split[0]
        Instances negatives = split[1]

        int pc = positives.size()
        int nc = negatives.size()

        double ratio = (double) pc / nc
        double targetRatio = params.target_class_ratio

        int seed = new Random(params.seed).nextInt()

        write "positives/negatives  ratio: $ratio  targetRatio: $targetRatio"

        if ( Math.abs(ratio-targetRatio)*data.size() < 1 ) {
            write "diference between ratio and target ratio is negligible"
            return data
        }

        if (params.supersample || params.subsample) {

            write "instances: " + descState(positives, negatives)

            if (params.supersample) {
                if (ratio < targetRatio) {
                    double multiplier = targetRatio / ratio
                    write "supersampling positives (multiplier: $multiplier)"
                    positives = WekaUtils.randomSample(multiplier, seed, positives)
                } else {
                    double multiplier = ratio / targetRatio
                    write "supersampling negatives (multiplier: $multiplier)"
                    negatives = WekaUtils.randomSample(multiplier, seed, negatives)
                }

            } else {
                if (ratio < targetRatio) {
                    double multiplier = ratio / targetRatio
                    write "subsampling negatives (multiplier: $multiplier)"

                    if (params.subsampl_high_protrusion_negatives) {
                        // sory by protrusion desc before subsampling
                        Attribute attr = negatives.attribute(ProtrusionFeature.NAME)
                        if (attr != null) {
                            negatives.sort(attr)
                            //negatives = WekaUtils.reverse(negatives)
                        }
                        negatives = WekaUtils.removeRatio(negatives, 1d - multiplier) // no randomization after sorting
                    } else {
                        negatives = WekaUtils.randomSample(multiplier, seed, negatives)
                    }
                } else {
                    double multiplier = targetRatio / ratio
                    write "subsampling positives (multiplier: $multiplier)"
                    positives = WekaUtils.randomSample(multiplier, seed, positives)
                }
            }

            write "instances: " + descState(positives, negatives)
        }

        data = WekaUtils.joinInstances([positives, negatives])
        data = WekaUtils.randomize(data, seed)

        return data
    }

    private static String descState(Instances positives, Instances negatives) {
        int pos = positives.size()
        int neg = negatives.size()

        double ratio = PerfUtils.round( (double)pos / neg, 6 )

        "positives: $pos, negatives: $neg, ratio: $ratio"
    }

    /**
     * modyfying data weights in place
     */
    private Instances balanceClassWeights(Instances data) {

        def split = WekaUtils.splitPositivesNegatives(data)
        Instances positives = split[0]
        Instances negatives = split[1]

        int pc = positives.size()
        int nc = negatives.size()

        double ratio = (double) pc / nc
        double targetWeightRatio = params.target_class_weight_ratio

        double posWeight = targetWeightRatio / ratio

        write "balancing class weights ... ratio: $ratio  target ratio: $targetWeightRatio  pos. weight: $posWeight"

        for (Instance inst : positives) {
            inst.setWeight(posWeight)
        }

        write "weighted ratio: ${weightedRatio(data)}"

        return data
    }

    private static double weightedRatio(Instances data) {
        double wp = 0
        double wn = 0

        for (Instance inst : data) {
            if (inst.classValue() == 0) {
                wn += inst.weight()
            } else {
                wp += inst.weight()
            }
        }

        return wp / wn
    }

}
