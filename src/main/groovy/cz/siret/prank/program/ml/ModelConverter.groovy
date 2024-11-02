package cz.siret.prank.program.ml

import cz.siret.prank.fforest.FasterForest
import cz.siret.prank.fforest.FasterTree
import cz.siret.prank.fforest.api.FlatBinaryForest
import cz.siret.prank.fforest.api.FlatBinaryForestBuilder
import cz.siret.prank.fforest2.FasterForest2
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.ATimer
import cz.siret.prank.utils.SysUtils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import groovyx.gpars.GParsPool
import hr.irb.fastRandomForest.FastRandomForest
import weka.classifiers.Classifier

import javax.annotation.Nullable

/**
 *
 */
@Slf4j
@CompileStatic
class ModelConverter implements Parametrized, Writable {


    Model applyConversions(Model model) {
        if (params.rf_flatten) {
            model = flattenRandomForest(model)
        }
        return model
    }

//===========================================================================================================//

    static List<Class> FLATTABLE_CLASSIFIERS = (List) [FastRandomForest, FasterForest, FasterForest2]
    static List<String> FLATTABLE_CLASSIFIER_NAMES = FLATTABLE_CLASSIFIERS*.simpleName

    static boolean isFlattableClassifier(Classifier c) {
        return SysUtils.isInstanceOfAny(c, FLATTABLE_CLASSIFIERS)
    }

    Model flattenRandomForest(Model model) {
        def c = model.classifier
        if (isFlattableClassifier(c)) {
            ATimer timer = ATimer.startTimer()

            write "Converting ${c.class.simpleName} to FlatBinaryForest"

            FlatBinaryForest fbf
            if (c instanceof FastRandomForest) {
                fbf = frfToFlatForest((FastRandomForest)c)
            } else if (c instanceof FasterForest) {
                fbf = ((FasterForest)c).toFlatBinaryForest(params.rf_flatten_as_legacy)
            } else { // FF2
                fbf = ((FasterForest2)c).toFlatBinaryForest(params.rf_flatten_as_legacy)
            }
            write " - flattened in:  $timer.formatted"

            return new Model("FlatBinaryForest_from_${model.label}", fbf)
        } else {
            log.warn "Cannot flatten classifier of type ${c.class.simpleName}. Flattable classifiers: ${FLATTABLE_CLASSIFIER_NAMES}"
            return model
        }
    }

//===========================================================================================================//


    @CompileDynamic
    FlatBinaryForest frfToFlatForest(FastRandomForest forest) {
        ATimer timer = ATimer.startTimer()

        int numAttributes = forest.@m_Info.numAttributes();
        List<Classifier> mTrees = Arrays.asList(forest.@m_bagger.@m_Classifiers)

        List<FasterTree> trees
        GParsPool.withPool(params.threads * 2) {
            trees = mTrees.collectParallel { frfTreeToFasterTree(it) }
        }

        write " - faster trees converted in:  $timer.formatted"

        return new FlatBinaryForestBuilder().buildFromFasterTrees(numAttributes, trees, params.rf_flatten_as_legacy)
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
