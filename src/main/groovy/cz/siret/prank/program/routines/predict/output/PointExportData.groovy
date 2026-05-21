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

    /** Whether to include pocket column in export (only when pocket assignments are available) */
    final boolean includePocket

    /** Number of fixed columns before features (3 + score? + pocket?) */
    private final int fixedColumns

    /** Index of the pocket column, or -1 if not present */
    private final int pocketColumnIndex

    /** Cached full header */
    private List<String> cachedHeader

    private PointExportData(List<LabeledPoint> labeledPoints,
                            List<FeatureVector> featureVectors,
                            List<String> featureHeader,
                            boolean includeScore,
                            boolean includePocket) {
        if (labeledPoints.size() != featureVectors.size()) {
            throw new IllegalArgumentException(
                "Size mismatch: ${labeledPoints.size()} points but ${featureVectors.size()} feature vectors")
        }
        this.labeledPoints = labeledPoints
        this.featureVectors = featureVectors
        this.featureHeader = featureHeader
        this.includeScore = includeScore
        this.includePocket = includePocket
        this.fixedColumns = 3 + (includeScore ? 1 : 0) + (includePocket ? 1 : 0)
        this.pocketColumnIndex = includePocket ? (3 + (includeScore ? 1 : 0)) : -1
    }

    // --- TableData Implementation ---

    @Override
    List<String> getHeader() {
        if (cachedHeader == null) {
            List<String> h = new ArrayList<>(fixedColumns + featureHeader.size())
            h.add("x"); h.add("y"); h.add("z")
            if (includeScore) h.add("score")
            if (includePocket) h.add("pocket")
            h.addAll(featureHeader)
            cachedHeader = h
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
        if (includePocket) {
            row[pocketColumnIndex] = lp.pocket
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
        } else if (includePocket && colIndex == pocketColumnIndex) {
            // Pocket column (only when included)
            for (int i = 0; i < n; i++) {
                column[i] = labeledPoints.get(i).pocket
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

    @Override
    ColumnType getColumnType(int colIndex) {
        return (includePocket && colIndex == pocketColumnIndex) ? ColumnType.INT : ColumnType.DOUBLE
    }

    // --- Convenience ---

    /** @deprecated Use {@link #getRowCount()} instead */
    @Deprecated
    int size() {
        return getRowCount()
    }

    // --- Factory Methods ---

    /**
     * Creates export data with score and pocket columns. Used by both
     * {@code predict} (full SAS) and {@code rescore} (pocket-only SAS) modes.
     */
    static PointExportData create(List<LabeledPoint> labeledPoints,
                                  List<FeatureVector> featureVectors,
                                  List<String> featureHeader) {
        return new PointExportData(labeledPoints, featureVectors, featureHeader, true, true)
    }

    /**
     * Creates export data without score or pocket column (for export-points command — no prediction).
     */
    static PointExportData createWithoutScores(List<LabeledPoint> labeledPoints,
                                               List<FeatureVector> featureVectors,
                                               List<String> featureHeader) {
        return new PointExportData(labeledPoints, featureVectors, featureHeader, false, false)
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
            return new PointExportData(labeledPoints, featureVectors, featureHeader, true, true)
        }
    }

}
