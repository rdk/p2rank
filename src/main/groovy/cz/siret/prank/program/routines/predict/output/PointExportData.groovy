package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.features.FeatureVector
import groovy.transform.CompileStatic

/**
 * Encapsulates data needed for exporting SAS points with their feature vectors and scores.
 */
@CompileStatic
class PointExportData {

    final List<LabeledPoint> labeledPoints
    final List<FeatureVector> featureVectors
    final List<String> featureHeader

    private PointExportData(List<LabeledPoint> labeledPoints,
                            List<FeatureVector> featureVectors,
                            List<String> featureHeader) {
        this.labeledPoints = labeledPoints
        this.featureVectors = featureVectors
        this.featureHeader = featureHeader
    }

    int size() {
        return labeledPoints.size()
    }

    /**
     * Creates export data from pre-collected lists.
     * Used when vectors are computed in batch (predict mode).
     */
    static PointExportData create(List<LabeledPoint> labeledPoints,
                                  List<FeatureVector> featureVectors,
                                  List<String> featureHeader) {
        return new PointExportData(labeledPoints, featureVectors, featureHeader)
    }

    /**
     * Creates a builder for incrementally collecting export data.
     * Used when vectors are computed one at a time (rescore mode).
     */
    static Builder builder(List<String> featureHeader) {
        return new Builder(featureHeader)
    }

    @CompileStatic
    static class Builder {
        private final List<String> featureHeader
        private final List<LabeledPoint> labeledPoints = new ArrayList<>()
        private final List<FeatureVector> featureVectors = new ArrayList<>()

        Builder(List<String> featureHeader) {
            this.featureHeader = featureHeader
        }

        void add(LabeledPoint point, FeatureVector vector) {
            labeledPoints.add(point)
            featureVectors.add(vector)
        }

        PointExportData build() {
            return new PointExportData(labeledPoints, featureVectors, featureHeader)
        }
    }

}
