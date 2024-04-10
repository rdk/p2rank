package cz.siret.prank.program.ml

import cz.siret.prank.fforest.FasterForest
import cz.siret.prank.fforest2.FasterForest2
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.Console
import cz.siret.prank.utils.Cutils
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.WekaUtils
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import hr.irb.fastRandomForest.FastRandomForest
import weka.classifiers.Classifier
import weka.classifiers.trees.RandomForest

import javax.annotation.Nullable

/**
 * Machine learning prediction model
 */
@CompileStatic
class Model {

    String label
    Classifier classifier

    Model(String label, Classifier classifier) {
        this.label = label
        this.classifier = Objects.requireNonNull(classifier)
    }

    void disableParalelism() {
        WekaUtils.disableParallelism(classifier)
    }

    boolean hasFeatureImportances() {
        return (classifier instanceof FastRandomForest)
                || (classifier instanceof FasterForest)
                || (classifier instanceof FasterForest2)
    }

    @Nullable
    List<Double> getFeatureImportances() {
        List<Double> res = null
        if (classifier instanceof FastRandomForest) {
            res = (classifier as FastRandomForest).featureImportances.toList()
            res = Cutils.head(res.size()-1, res)                 // random forest returns column for class
        } else if (classifier instanceof FasterForest) {
            res = (classifier as FasterForest).featureImportances.toList()
            res = Cutils.head(res.size()-1, res)
        }  else if (classifier instanceof FasterForest2) {
            res = (classifier as FasterForest2).featureDropoutImportance.toList()
            res = Cutils.head(res.size()-1, res)
        }
        return res
    }

    void saveToFile(String fname) {
        WekaUtils.saveClassifier((Classifier)classifier, fname)
        Console.write "model saved to file $fname (${Futils.sizeMBFormatted(fname)} MB)"
    }


    /**
     * Load from file and apply conversions
     * @param fname
     * @return
     */
    static Model load(String fname) {
        Model model = loadFromFile(fname)
        model = new ModelConverter().applyConversions(model)
        return model
    }

    static Model loadFromFile(String fname) {
        if (fname.contains(".model2")) {
            return loadFromFileV2(fname)
        } else {
            return loadFromFileV1(fname)
        }
    }

    private static Model loadFromFileV2(String fname) {
        //fname += ".zst"
        Classifier classifier = WekaUtils.loadClassifier(Futils.inputStream(fname))
        return new Model(Futils.shortName(fname), classifier)
    }

    private static Model loadFromFileV1(String fname) {
        return new Model(Futils.shortName(fname), WekaUtils.loadClassifier(fname))
    }

    static Model createNewFromParams(Params params) {
        Classifier classifier = ClassifierFactory.createClassifier(params)
        String label = classifier.class.simpleName
        return new Model(label, classifier)
    }

    @CompileDynamic
    Info getInfo() {

        Info info = new Info()

        if (classifier instanceof RandomForest) {
            RandomForest rf = (RandomForest)classifier
            info.isForest    = true
            info.numTrees    = rf.numIterations
            info.numFeatures = rf.@m_data?.enumerateAttributes()?.toList()?.size()
            info.maxDepth    = rf.maxDepth
        } else if (classifier instanceof FastRandomForest) {
            FastRandomForest rf = (FastRandomForest)classifier
            info.isForest    = true
            info.numTrees    = rf.numTrees
            info.numFeatures = rf.@m_Info?.enumerateAttributes()?.toList()?.size()
            info.maxDepth    = rf.maxDepth
        } else if (classifier instanceof FasterForest) {
            FasterForest rf = (FasterForest)classifier
            info.isForest    = true
            info.numTrees    = rf.numTrees
            info.numFeatures = rf.@m_Info?.enumerateAttributes()?.toList()?.size()
            info.maxDepth    = rf.maxDepth
        } else if (classifier instanceof FasterForest2) {
            FasterForest2 rf = (FasterForest2)classifier
            info.isForest    = true
            info.numTrees    = rf.numTrees
            info.numFeatures = rf.@m_Info?.enumerateAttributes()?.toList()?.size()
            info.maxDepth    = rf.maxDepth
        } else {
            // nothing
        }

        return info
    }

    static class Info {
        boolean isForest = false
        Integer numTrees    = null
        Integer numFeatures = null
        Integer maxDepth    = null
    }

}
