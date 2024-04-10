package cz.siret.prank.prediction.pockets.rescorers;

import cz.siret.prank.features.FeatureExtractor;
import cz.siret.prank.features.FeatureVector;
import cz.siret.prank.fforest.FasterForest;
import cz.siret.prank.fforest.api.FlatBinaryForest;
import cz.siret.prank.fforest2.FasterForest2;
import cz.siret.prank.program.ml.Model;
import cz.siret.prank.program.params.Params;
import cz.siret.prank.utils.PerfUtils;
import cz.siret.prank.utils.WekaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.util.List;

import static cz.siret.prank.prediction.pockets.PointScoreCalculator.normalizedScore;

/**
 * Encapsulates prediction of distribution by a model
 */
public interface InstancePredictor {

    static final Logger log = LoggerFactory.getLogger(InstancePredictor.class);

    double predictPositive(FeatureVector vect) throws Exception;

    default double[] predictBatch(List<FeatureVector> vectors) throws Exception {
        if (Params.getInst().getRf_batch_prediction()) {
            return _predictBatchAsBatch(vectors);
        } else {
            return _predictBatchOneByOne(vectors);
        }
    }

    default double[] _predictBatchOneByOne(List<FeatureVector> vectors) throws Exception {
        int n = vectors.size();
        double[] res = new double[n];
        for (int i=0; i!=n; ++i) {
            res[i] = predictPositive(vectors.get(i));
        }
        return res;
    }

    default double[] _predictBatchAsBatch(List<FeatureVector> vectors) throws Exception {
        int n = vectors.size();
        double[][] arrays = new double[n][];
        for (int i=0; i!=n; ++i) {
            arrays[i] = vectors.get(i).getArray();
        }
        return predictBatchArrays(arrays);
    }

    double[] predictBatchArrays(double[][] arrays) throws Exception;

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
                    public double[] predictBatchArrays(double[][] arrays)  {
                        return ff.predictForBatch(arrays);
                    }

                    @Override
                    public double[] getDistributionForPoint(FeatureVector vect) {
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
                public double[] predictBatchArrays(double[][] arrays) {
                    return ff.predictForBatch(arrays);
                }

                @Override
                public double[] getDistributionForPoint(FeatureVector vect) {
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
                public double[] predictBatchArrays(double[][] arrays) {
                    return ff.predictForBatch(arrays);
                }

                @Override
                public double[] getDistributionForPoint(FeatureVector vect) {
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
        public double[] predictBatchArrays(double[][] arrays) throws Exception {
            int n = arrays.length;
            double[] res = new double[n];
            for (int i=0; i!=n; ++i) {
                res[i] = normalizedScore(getDistributionForPoint(arrays[i]));
            }
            return res;
        }

        @Override
        public double[] getDistributionForPoint(FeatureVector vect) throws Exception {
            return getDistributionForPoint(vect.getArray());
        }

        public double[] getDistributionForPoint(double[] vect) throws Exception {
            PerfUtils.arrayCopy(vect, alloc);
            return classifier.distributionForInstance(auxInst);
        }
        
    }
    
}
