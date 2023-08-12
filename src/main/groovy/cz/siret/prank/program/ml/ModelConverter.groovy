package cz.siret.prank.program.ml

import cz.siret.prank.fforest.FasterTree
import cz.siret.prank.fforest.api.FlatBinaryForest
import cz.siret.prank.fforest.api.FlatBinaryForestBuilder
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.program.routines.traineval.TrainEvalRoutine
import cz.siret.prank.utils.ATimer
import cz.siret.prank.utils.Writable
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovyx.gpars.GParsPool
import hr.irb.fastRandomForest.FastRandomForest
import weka.classifiers.Classifier

import javax.annotation.Nullable

/**
 *
 */
@CompileStatic
class ModelConverter implements Parametrized, Writable {

    Model applyConversions(Model model) {
        if (params.rf_flatten) {
            if (model.classifier instanceof FastRandomForest) {
                write "Converting FastRandomForest to FlatBinaryForest"
                FlatBinaryForest fbf = toFlatForest((FastRandomForest)model.classifier)
                return new Model("FlatBinaryForest_from_${model.label}", fbf)
            }
        }
        return model
    }

//===========================================================================================================//

    @CompileDynamic
    FlatBinaryForest toFlatForest(FastRandomForest forest) {

        ATimer timer = ATimer.startTimer()

        List<FasterTree> trees
        List<Classifier> mTrees = Arrays.asList(forest.@m_bagger.@m_Classifiers)

        GParsPool.withPool(params.threads * 2) {
            trees = mTrees.collectParallel { toFasterTree(it) }
        }

        write " - taster trees collected in:  $timer.formatted"

        int numAttributes = forest.@m_Info.numAttributes();
        FlatBinaryForest res = new FlatBinaryForestBuilder().buildFromFasterTrees(numAttributes, trees, true)  // params.use_only_positive_score

        write " - flattened in:  $timer.formatted"

        return res
    }

    /**
     *
     * @param fastRandomTree  hr.irb.fastRandomForest.FastRandomTree
     * @return
     */
    @CompileDynamic
    FasterTree toFasterTree(@Nullable Object fastRandomTree) {
        if (fastRandomTree == null) return null

        Classifier[] successors = fastRandomTree.@m_Successors
        FasterTree childLeft = null
        FasterTree childRight = null
        if (successors != null) {
            childLeft = toFasterTree(successors[0])
            childRight = toFasterTree(successors[1])
        }

        int attribute = fastRandomTree.@m_Attribute
        double splitPoint = fastRandomTree.@m_SplitPoint
        double[] classProbs = fastRandomTree.@m_ClassProbs

        return new FasterTree(childLeft, childRight, attribute, splitPoint, classProbs)
    }

}
