package cz.siret.prank.prediction.pockets.rescorers;

import cz.siret.prank.features.FeatureExtractor;
import cz.siret.prank.features.FeatureVector;
import cz.siret.prank.fforest.FasterForest;
import cz.siret.prank.fforest.api.FlatBinaryForest;
import cz.siret.prank.fforest2.FasterForest2;
import cz.siret.prank.program.ml.Model;
import cz.siret.prank.utils.PerfUtils;
import cz.siret.prank.utils.WekaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.core.DenseInstance;
import weka.core.Instances;

import static cz.siret.prank.prediction.pockets.PointScoreCalculator.normalizedScore;

/**
 * Encapsulates prediction of distribution by a model
 */
public interface InstancePredictor {

    static final Logger log = LoggerFactory.getLogger(InstancePredictor.class);

    double predictPositive(FeatureVector vect) throws Exception;
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
                log.info("Creating faster InstancePredictor using distributionForAttributes()");

                res = new InstancePredictor() { // predictor using faster distributionForAttributes()
                    final FasterForest ff = (FasterForest) classifier;

                    @Override
                    public double predictPositive(FeatureVector vect) {
                        double[] hist = ff.distributionForAttributes(vect.getArray(), 2);
                        return normalizedScore(hist);
                    }

                    @Override
                    public double[] getDistributionForPoint(FeatureVector vect) throws Exception {
                        return ff.distributionForAttributes(vect.getArray(), 2);
                    }
                };
            }

        } else if (classifier instanceof FasterForest2) {
            res = new InstancePredictor() { // predictor using faster distributionForAttributes()
                final FasterForest2 ff = (FasterForest2) classifier;

                @Override
                public double predictPositive(FeatureVector vect) {
                    double[] hist = ff.distributionForAttributes(vect.getArray(), 2);
                    return normalizedScore(hist);
                }

                @Override
                public double[] getDistributionForPoint(FeatureVector vect) throws Exception {
                    return ff.distributionForAttributes(vect.getArray(), 2);
                }
            };
        } else if (classifier instanceof FlatBinaryForest) {
            res = new InstancePredictor() { // predictor using faster distributionForAttributes()
                final FlatBinaryForest ff = (FlatBinaryForest) classifier;

                @Override
                public double predictPositive(FeatureVector vect) {
                    return ff.predict(vect.getArray());
                }

                @Override
                public double[] getDistributionForPoint(FeatureVector vect) throws Exception {
                    double p = predictPositive(vect);
                    return new double[] {1d-p, p};
                }
            };
        } 

        if (res == null) {
            log.info("Creating WekaInstancePredictor");
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
            auxInst = new DenseInstance(1, alloc);
            Instances auxWekaDataset = WekaUtils.createDatasetWithBinaryClass(proteinExtractor.getVectorHeader());
            auxInst.setDataset(auxWekaDataset);

        }

        @Override
        public double predictPositive(FeatureVector vect) throws Exception {
            double[] hist = getDistributionForPoint(vect);
            return normalizedScore(hist);  // not all classifiers give histogram that sums up to 1
        }

        @Override
        public double[] getDistributionForPoint(FeatureVector vect) throws Exception {
            PerfUtils.arrayCopy(vect.getArray(), alloc);
            return classifier.distributionForInstance(auxInst);
        }
        
    }
    
}
