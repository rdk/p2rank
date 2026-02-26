package cz.siret.prank.program.ml

import cz.siret.prank.fforest.FasterForest
import cz.siret.prank.fforest.FasterTree
import cz.siret.prank.fforest.api.*
import cz.siret.prank.fforest2.FasterForest2
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.ATimer
import cz.siret.prank.utils.Parallel
import cz.siret.prank.utils.SysUtils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import hr.irb.fastRandomForest.FastRandomForest
import org.apache.commons.lang3.StringUtils
import weka.classifiers.Classifier
import weka.classifiers.trees.RandomForest
import weka.core.Instances

import javax.annotation.Nullable

/**
 * Utility class for converting models to different formats, e.g. flattening random forests to a more efficient format for prediction.
 */
@Slf4j
@CompileStatic
class ModelConverter implements Parametrized, Writable {

    Model applyConversions(Model model) {

        if (params.rf_flatten) {
            if (!StringUtils.isBlank(params.rf_flatten_target)) {
                model = flattenRandomForest(model, params.rf_flatten_target)
            } else {
                // useful as no-op option when running ploop for rf_flatten_target param
                log.info "'rf_flatten_target' parameter is empty, no flattening is applied."
            }

        }
        return model
    }

//===========================================================================================================//

    static List<Class> FLATTABLE_CLASSIFIERS = [RandomForest, FastRandomForest, FasterForest, FasterForest2, LegacyFlatBinaryForest, FlatBinaryForest] as List<Class>
    static List<String> FLATTABLE_CLASSIFIER_NAMES = FLATTABLE_CLASSIFIERS*.simpleName

    static boolean isFlattableClassifier(Object c) {
        return SysUtils.isInstanceOfAny(c, FLATTABLE_CLASSIFIERS)
    }

    Model flattenRandomForest(Model model, String targetType) {
        def c = model.classifier
        if (isFlattableClassifier(c)) {
            ATimer timer = ATimer.startTimer()

            write "Converting ${c.class.simpleName} to $targetType"

            FasterForestConverter.ForestType forestType
            try {
                forestType = FasterForestConverter.ForestType.valueOf(targetType)
            } catch (Exception e) {
                throw new IllegalArgumentException("Unknown target forest type '$targetType'. Supported types: ${FasterForestConverter.ForestType.values()*.name()}.")
            }

            if (forestType == FasterForestConverter.ForestType.NativePanamaForest) {
                if (!NativePanamaForest.isAvailable()) {
                    throw new IllegalStateException("NativePanamaForest is not available on this platform. Cannot flatten to NativePanamaForest.")
                }
            } else if (forestType == FasterForestConverter.ForestType.NativePanamaForestAvx2) {
                if (!NativePanamaForestAvx2.isAvx2Available()) {
                    throw new IllegalStateException("NativePanamaForestAvx2 is not available on this platform. Cannot flatten to NativePanamaForestAvx2.")
                }
            }

            BinaryForest flatForest
            TrainableFasterForest trainableForest

            // prepare trainable forest for flattening, if needed (e.g. FastRandomForest needs to be converted to TrainableFasterForest first)
            if (c instanceof TrainableFasterForest) {

                trainableForest = (TrainableFasterForest) c

            } else if (c instanceof FastRandomForest) {

                trainableForest = frfToTrainableBinaryForest((FastRandomForest) c)

            } else if (c instanceof LegacyFlatBinaryForest) {
                // LegacyFlatBinaryForest must go first since it extends FlatBinaryForest and allows for lossless conversions (keeps probabilities of both classes)

                trainableForest = FlatBinaryForestBuilder.toFasterTreeForest((LegacyFlatBinaryForest) c)

            } else if (c instanceof FlatBinaryForest) {

                trainableForest = FlatBinaryForestBuilder.toFasterTreeForest((FlatBinaryForest) c)

            } else if (c instanceof RandomForest) {

                trainableForest = WekaRandomForestConverter.toFasterTreeForest((RandomForest) c)

            } else {
                throw new IllegalStateException("Unexpected flattable forest type: ${c.class.simpleName}")
            }

            // convert to target flat forest type
            flatForest = FasterForestConverter.convertFasterForest(trainableForest, forestType)

            String newClassName = flatForest.getClass().simpleName
            write " - flattened to ${newClassName} in:  $timer.formatted"

            return new Model("${newClassName}_from_${model.label}", flatForest)
        } else {
            log.warn "Cannot flatten classifier of type ${c.class.simpleName}. Flattable classifiers: ${FLATTABLE_CLASSIFIER_NAMES}"
            return model
        }
    }

//===========================================================================================================//

    @CompileDynamic
    TrainableFasterForest frfToTrainableBinaryForest(FastRandomForest forest) {
        int numAttributes = forest.@m_Info.numAttributes()
        List<Classifier> mTrees = Arrays.asList(forest.@m_bagger.@m_Classifiers)
        List<FasterTree> trees = Parallel.collectParallel(mTrees, params.threads * 2) { frfTreeToFasterTree(it) }


        return new TrainableFasterForest() {
            @Override
            int getNumAttributes() {
                return numAttributes
            }

            @Override
            List<FasterTree> getTrees() {
                return trees
            }

            @Override
            void buildClassifier(Instances instances) throws Exception {
                // NO-OP
            }
        }
    }

    /**
     *
     * @param fastRandomTree  hr.irb.fastRandomForest.FastRandomTree
     * @return
     */
    @CompileDynamic
    FasterTree frfTreeToFasterTree(@Nullable Object fastRandomTree) {
        if (fastRandomTree == null) return null

        Classifier[] successors = fastRandomTree.@m_Successors
        FasterTree childLeft = null
        FasterTree childRight = null
        if (successors != null) {
            childLeft = frfTreeToFasterTree(successors[0])
            childRight = frfTreeToFasterTree(successors[1])
        }

        int attribute = fastRandomTree.@m_Attribute
        double splitPoint = fastRandomTree.@m_SplitPoint
        double[] classProbs = fastRandomTree.@m_ClassProbs

        return new FasterTree(childLeft, childRight, attribute, splitPoint, classProbs)
    }

}
