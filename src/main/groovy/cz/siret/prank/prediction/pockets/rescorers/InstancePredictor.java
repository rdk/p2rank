package cz.siret.prank.prediction.pockets.rescorers;

import cz.siret.prank.features.FeatureExtractor;
import cz.siret.prank.features.FeatureVector;
import cz.siret.prank.fforest.FasterForest;
import cz.siret.prank.fforest2.FasterForest2;
import cz.siret.prank.program.ml.Model;
import cz.siret.prank.utils.PerfUtils;
import cz.siret.prank.utils.WekaUtils;
import weka.classifiers.Classifier;
import weka.core.DenseInstance;
import weka.core.Instances;

/**
 * Encapsulates prediction of distribution by a model
 */
public interface InstancePredictor {

    double[] getDistributionForPoint(FeatureVector vect) throws Exception;


    static InstancePredictor create(Model model, FeatureExtractor<?> proteinExtractor) {
        Classifier classifier = model.getClassifier();

        InstancePredictor res = null;

        if (classifier instanceof FasterForest) {

            boolean isV2 = false;
            try {
                if (((FasterForest)classifier).isVersion2()) {
                    isV2 = true;
                }
            } catch (Throwable e) {
                // do nothing
            }

            if (isV2) {
                res = new InstancePredictor() { // predictor using faster distributionForAttributes()
                    final FasterForest ff = (FasterForest) classifier;
                    @Override
                    public double[] getDistributionForPoint(FeatureVector vect) {
                        return ff.distributionForAttributes(vect.getArray(), 2);
                    }
                };
            }

        }

        if (res == null) {
            res = new WekaInstancePredictor(model.getClassifier(), proteinExtractor);
        }

        return res;
    }

    static class WekaInstancePredictor implements InstancePredictor {

        private final Classifier classifier;

        // auxiliary for weka
        private final double[] alloc;
        private final DenseInstance auxInst;

        public WekaInstancePredictor(Classifier classifier, FeatureExtractor<?> proteinExtractor) {
            this.classifier = classifier;

            alloc = new double[proteinExtractor.getVectorHeader().size() + 1]; // one additional for stupid weka class
            auxInst = new DenseInstance( 1, alloc );
            Instances auxWekaDataset = WekaUtils.createDatasetWithBinaryClass(proteinExtractor.getVectorHeader());
            auxInst.setDataset(auxWekaDataset);

        }

        @Override
        public double[] getDistributionForPoint(FeatureVector vect) throws Exception {
            PerfUtils.arrayCopy(vect.getArray(), alloc);
            return classifier.distributionForInstance(auxInst);
        }
    }

}
