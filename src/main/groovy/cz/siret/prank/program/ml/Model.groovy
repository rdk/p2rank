package cz.siret.prank.program.ml

import cz.siret.prank.fforest.FasterForest
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.ConsoleWriter
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.WekaUtils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import hr.irb.fastRandomForest.FastRandomForest
import weka.classifiers.Classifier

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
        return (classifier instanceof FastRandomForest) || (classifier instanceof FasterForest)
    }

    @Nullable
    List<Double> getFeatureImportances() {
        if (classifier instanceof  FastRandomForest) {
            return (classifier as FastRandomForest).featureImportances.toList()
        } else if (classifier instanceof FasterForest) {
            return (classifier as FasterForest).featureImportances.toList()
        }
        return null
    }

    void saveToFile(String fname) {
        WekaUtils.saveClassifier((Classifier)classifier, fname)
        ConsoleWriter.write "model saved to file $fname (${Futils.sizeMBFormatted(fname)} MB)"
    }

    static Model loadFromFile(String fname) {
        return new Model(Futils.shortName(fname), WekaUtils.loadClassifier(fname))
    }

    static Model createNewFromParams(Params params) {
        Classifier classifier = ClassifierFactory.createClassifier(params)
        String label = classifier.class.simpleName
        return new Model(label, classifier)
    }



}
