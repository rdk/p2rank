package cz.siret.prank.collectors

import cz.siret.prank.features.implementation.ProtrusionFeature
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.WekaUtils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import weka.core.Attribute
import weka.core.Instance
import weka.core.Instances

import static cz.siret.prank.utils.Formatter.format
import static cz.siret.prank.utils.WekaUtils.isPositiveInstance

@Slf4j
@CompileStatic
class DataPreprocessor implements Parametrized, Writable {

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

        write "positives/negatives  ratio: ${fmt ratio}  targetRatio: ${fmt targetRatio}"

        if ( Math.abs(ratio-targetRatio)*data.size() < 1 ) {
            write "diference between ratio and target ratio is negligible"
            return data
        }

        if (params.supersample || params.subsample) {

            write "instances: " + descState(positives, negatives)

            if (params.supersample) {

                if (ratio < targetRatio) {
                    double multiplier = targetRatio / ratio
                    write "supersampling positives (multiplier: ${fmt multiplier})"
                    positives = WekaUtils.randomSample(multiplier, seed, positives)
                } else {
                    double multiplier = ratio / targetRatio
                    write "supersampling negatives (multiplier: ${fmt multiplier})"
                    negatives = WekaUtils.randomSample(multiplier, seed, negatives)
                }

            } else { // subsample

                if (ratio < targetRatio) {
                    double multiplier = ratio / targetRatio
                    write "subsampling negatives (multiplier: ${fmt multiplier})"

                    if (params.subsampl_high_protrusion_negatives) {
                        // sort by protrusion desc before subsampling
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

        double pos_ratio = (double)pos / (pos + neg)
        double ratio = (double)pos / neg

        "positives: $pos, negatives: $neg, pos/neg: ${fmt ratio} pos/all: ${fmt pos_ratio}"
    }

    private static String fmt(double d) {
        format(d, 5)
    }

    /**
     * modifying data weights in place
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

        write "balancing class weights ... ratio: ${fmt ratio}" +
                "  target ratio: ${fmt targetWeightRatio}  pos. weight: ${fmt posWeight}"

        for (Instance inst : data) {
            if (isPositiveInstance(inst)) {
                inst.setWeight(posWeight)
            }
        }

        write "weighted ratio: ${fmt weightedRatio(data)}"

        return data
    }

    private static double weightedRatio(Instances data) {
        double wp = 0
        double wn = 0

        for (Instance inst : data) {
            if (isPositiveInstance(inst)) {
                wp += inst.weight()
            } else {
                wn += inst.weight()
            }
        }

        return wp / wn
    }

}
