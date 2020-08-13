package cz.siret.prank.collectors

import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.utils.PerfUtils
import groovy.transform.CompileStatic

/**
 * Collects feature vectors (possibly with classes) for further training
 */
@CompileStatic
abstract class VectorCollector {

    abstract Result collectVectors(PredictionPair pair, ProcessedItemContext context);

    abstract List<String> getHeader();

    String getHeaderCSV() {
        return getHeader().join(",")
    }

    static final class Result {
        int positives = 0
        int negatives = 0
        List<FeatureVector> vectors

        Result(int initSize) {
            vectors = new ArrayList<>(initSize)
        }

        Result() {
            this(128)
        }

        void add(List<Double> vect) {
            vectors.add(new DoubleVector(vect))
        }

        void add(double[] features, double clazz) {
            vectors.add(new DoubleVector(PerfUtils.extendArray(features, clazz)))
        }

        void addBinary(double[] features, boolean positive) {
            double doubleClass = positive ? 1d : 0d
            vectors.add(new DoubleVector(PerfUtils.extendArray(features, doubleClass)))
            if (positive) {
                positives++
            } else {
                negatives++
            }
        }

        void addAll(Result r) {
            positives += r.positives
            negatives += r.negatives
            vectors.addAll(r.vectors)
        }

        String toCSV() {
            StringBuilder sb = new StringBuilder()
            for (FeatureVector v : vectors) {
                sb.append(v.toCSV())
                sb.append("\n")
            }
            return sb.toString()
        }

        @Override
        String toString() {
            return "all:${vectors.size()}  positives:$positives negatives:$negatives"
        }
        
    }

}
