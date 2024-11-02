package cz.siret.prank.program.ml

import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.PrankFeatureExtractor
import cz.siret.prank.fforest.FasterForest
import cz.siret.prank.fforest.api.FlatBinaryForest
import cz.siret.prank.fforest2.FasterForest2
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.Console
import cz.siret.prank.utils.Cutils
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.WekaUtils
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import hr.irb.fastRandomForest.FastRandomForest
import weka.classifiers.Classifier
import weka.classifiers.trees.RandomForest

import javax.annotation.Nullable

/**
 * Machine learning prediction model
 */
@Slf4j
@CompileStatic
class Model {

    String label
    Classifier classifier

    Model(String label, Classifier classifier) {
        this.label = label
        this.classifier = Objects.requireNonNull(classifier)
    }

    Model disableParallelism() {
        WekaUtils.disableParallelism(classifier)
        return this
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

    /**
     * Load from file (v1 and v2 formats) or directory (v3) and apply conversions.
     * Disable model parallelism if available.
     *
     * @param fileOrDir
     * @return
     */
    static Model load(String fileOrDir) {
        Model model = loadFromFileOrDir(fileOrDir)
        model = new ModelConverter().applyConversions(model)

        model.disableParallelism()

        return model
    }

    /**
     * Load from file (v1 and v2 formats) or directory (v3).
     *
     * No conversions applied.
     *
     * @param fileOrDir
     * @return
     */
    static Model loadFromFileOrDir(String fileOrDir) {
        Model model

        if (Futils.isDirectory(fileOrDir)) {
            model = loadFromDirectoryV3(fileOrDir)
        } else {
            model = loadFromFileV1V2(fileOrDir)
        }

        return model
    }

    void saveToFile(String fname) {
        WekaUtils.saveClassifier((Classifier)classifier, fname)
        Console.write "model saved to file $fname (${Futils.sizeMBFormatted(fname)} MB)"
    }

    void saveToDirectoryV3(String dir) {
        log.info "Saving model to directory (v3 format): $dir"

        Futils.mkdirs(dir)

        String fname = dir + "/model.zst"
        int zstd_level = 16  // 16 seems to be fastest to load using zstd benchmark (for flattened models)

        log.info "Serializing model to $fname (zstd level: $zstd_level)"
        Futils.serializeToZstd(fname, classifier, zstd_level)

        Console.write "model saved to file $fname (${Futils.sizeMBFormatted(fname)} MB)"

        PrankFeatureExtractor fe = (PrankFeatureExtractor) FeatureExtractor.createFactory()
        List<String> subFeatureHeader = fe.vectorHeader

        Futils.writeFile(dir + "/features.txt", subFeatureHeader.join("\n"))
    }

//===========================================================================================================//

    /**
     * Model V3 format is a directory with classifier in model.zst file
     */
    static Model loadFromDirectoryV3(String dir) {
        log.info "Loading model from directory (v3 format): $dir"
        Classifier classifier = WekaUtils.loadClassifier(Futils.inputStream(dir + "/model.zst"))
        return new Model(Futils.shortName(dir), classifier)
    }

//===========================================================================================================//

    static Model loadFromFileV1V2(String fname) {
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
        } else if (classifier instanceof FlatBinaryForest) {
            FlatBinaryForest rf = (FlatBinaryForest)classifier
            info.isForest    = true
            info.numTrees    = rf.numTrees
            info.numFeatures = rf.numAttributes
            info.maxDepth    = rf.maxDepth
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
