package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.features.FeatureVector
import groovy.transform.CompileStatic

/**
 * Encapsulates data needed for exporting SAS points with their feature vectors and scores.
 * Implements TableData for generic export via TableExporter.
 */
@CompileStatic
class PointExportData implements TableData {

    final List<LabeledPoint> labeledPoints
    final List<FeatureVector> featureVectors
    final List<String> featureHeader

    /** Cached full header: [x, y, z, score, ...featureHeader] */
    private List<String> cachedHeader

    private PointExportData(List<LabeledPoint> labeledPoints,
                            List<FeatureVector> featureVectors,
                            List<String> featureHeader) {
        if (labeledPoints.size() != featureVectors.size()) {
            throw new IllegalArgumentException(
                "Size mismatch: ${labeledPoints.size()} points but ${featureVectors.size()} feature vectors")
        }
        this.labeledPoints = labeledPoints
        this.featureVectors = featureVectors
        this.featureHeader = featureHeader
    }

    // --- TableData Implementation ---

    @Override
    List<String> getHeader() {
        if (cachedHeader == null) {
            cachedHeader = ["x", "y", "z", "score"] + featureHeader
        }
        return cachedHeader
    }

    @Override
    int getRowCount() {
        return labeledPoints.size()
    }

    @Override
    double[] getRow(int index) {
        LabeledPoint lp = labeledPoints.get(index)
        double[] coords = lp.getCoords()
        double[] features = featureVectors.get(index).getArray()

        double[] row = new double[4 + features.length]
        row[0] = coords[0]
        row[1] = coords[1]
        row[2] = coords[2]
        row[3] = lp.score
        System.arraycopy(features, 0, row, 4, features.length)
        return row
    }

    /**
     * Optimized column access for columnar formats (Arrow, Parquet).
     * Avoids per-row array allocation overhead.
     */
    @Override
    double[] getColumn(int colIndex) {
        int n = labeledPoints.size()
        double[] column = new double[n]

        if (colIndex < 3) {
            // Coordinate columns: x, y, z
            for (int i = 0; i < n; i++) {
                column[i] = labeledPoints.get(i).getCoords()[colIndex]
            }
        } else if (colIndex == 3) {
            // Score column
            for (int i = 0; i < n; i++) {
                column[i] = labeledPoints.get(i).score
            }
        } else {
            // Feature columns
            int featureIndex = colIndex - 4
            for (int i = 0; i < n; i++) {
                column[i] = featureVectors.get(i).getArray()[featureIndex]
            }
        }
        return column
    }

    // --- Convenience ---

    /** @deprecated Use {@link #getRowCount()} instead */
    @Deprecated
    int size() {
        return getRowCount()
    }

    // --- Factory Methods ---

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
