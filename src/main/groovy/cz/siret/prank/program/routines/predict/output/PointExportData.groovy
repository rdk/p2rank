package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.features.FeatureVector
import groovy.transform.CompileStatic

/**
 * Encapsulates data needed for exporting SAS points with their feature vectors and optionally scores.
 * Implements TableData for generic export via TableExporter.
 */
@CompileStatic
class PointExportData implements TableData {

    final List<LabeledPoint> labeledPoints
    final List<FeatureVector> featureVectors
    final List<String> featureHeader

    /** Whether to include score column in export (false for export-points command) */
    final boolean includeScore

    /** Number of fixed columns before features (3 without score, 4 with score) */
    private final int fixedColumns

    /** Cached full header */
    private List<String> cachedHeader

    private PointExportData(List<LabeledPoint> labeledPoints,
                            List<FeatureVector> featureVectors,
                            List<String> featureHeader,
                            boolean includeScore) {
        if (labeledPoints.size() != featureVectors.size()) {
            throw new IllegalArgumentException(
                "Size mismatch: ${labeledPoints.size()} points but ${featureVectors.size()} feature vectors")
        }
        this.labeledPoints = labeledPoints
        this.featureVectors = featureVectors
        this.featureHeader = featureHeader
        this.includeScore = includeScore
        this.fixedColumns = includeScore ? 4 : 3
    }

    // --- TableData Implementation ---

    @Override
    List<String> getHeader() {
        if (cachedHeader == null) {
            List<String> prefix = includeScore ? ["x", "y", "z", "score"] : ["x", "y", "z"]
            cachedHeader = prefix + featureHeader
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
        double[] features = featureVectors.get(index).getArray()

        double[] row = new double[fixedColumns + features.length]
        row[0] = lp.getX()
        row[1] = lp.getY()
        row[2] = lp.getZ()
        if (includeScore) {
            row[3] = lp.score
        }
        System.arraycopy(features, 0, row, fixedColumns, features.length)
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
                LabeledPoint p = labeledPoints.get(i)
                column[i] = colIndex == 0 ? p.getX() : colIndex == 1 ? p.getY() : p.getZ()
            }
        } else if (includeScore && colIndex == 3) {
            // Score column (only when included)
            for (int i = 0; i < n; i++) {
                column[i] = labeledPoints.get(i).score
            }
        } else {
            // Feature columns
            int featureIndex = colIndex - fixedColumns
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
     * Creates export data with score column (for predict/rescore).
     */
    static PointExportData create(List<LabeledPoint> labeledPoints,
                                  List<FeatureVector> featureVectors,
                                  List<String> featureHeader) {
        return new PointExportData(labeledPoints, featureVectors, featureHeader, true)
    }

    /**
     * Creates export data without score column (for export-points command).
     */
    static PointExportData createWithoutScores(List<LabeledPoint> labeledPoints,
                                               List<FeatureVector> featureVectors,
                                               List<String> featureHeader) {
        return new PointExportData(labeledPoints, featureVectors, featureHeader, false)
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
            return new PointExportData(labeledPoints, featureVectors, featureHeader, true)
        }
    }

}
