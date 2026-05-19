package cz.siret.prank.program.routines.predict.output

import groovy.transform.CompileStatic

/**
 * Simple TableData implementation for testing.
 * Stores data as a list of row arrays.
 */
@CompileStatic
class ArrayTableData implements TableData {
    final List<String> header
    final List<double[]> rows
    final Set<Integer> intColumnIndices
    /** colIndex -> per-row string values; length must equal rows.size() */
    final Map<Integer, String[]> stringColumns

    ArrayTableData(List<String> header, List<double[]> rows) {
        this(header, rows, Collections.<Integer>emptySet(), Collections.<Integer, String[]>emptyMap())
    }

    ArrayTableData(List<String> header, List<double[]> rows, Set<Integer> intColumnIndices) {
        this(header, rows, intColumnIndices, Collections.<Integer, String[]>emptyMap())
    }

    ArrayTableData(List<String> header, List<double[]> rows,
                   Set<Integer> intColumnIndices, Map<Integer, String[]> stringColumns) {
        this.header = header
        this.rows = rows
        this.intColumnIndices = intColumnIndices
        this.stringColumns = stringColumns
    }

    @Override
    List<String> getHeader() { header }

    @Override
    int getRowCount() { rows.size() }

    @Override
    double[] getRow(int index) { rows[index] }

    @Override
    ColumnType getColumnType(int colIndex) {
        if (stringColumns.containsKey(colIndex)) return ColumnType.STRING
        return intColumnIndices.contains(colIndex) ? ColumnType.INT : ColumnType.DOUBLE
    }

    @Override
    String getString(int rowIndex, int colIndex) {
        String[] col = stringColumns.get(colIndex)
        if (col == null) {
            throw new UnsupportedOperationException("Column " + colIndex + " is not STRING")
        }
        return col[rowIndex]
    }

    // --- Factory methods for fluent test creation ---

    static ArrayTableData of(List<String> header, List<double[]> rows) {
        new ArrayTableData(header, rows)
    }

    static ArrayTableData ofWithInts(List<String> header, List<double[]> rows, Integer... intColIndices) {
        new ArrayTableData(header, rows, intColIndices.toList() as Set<Integer>)
    }

    static ArrayTableData ofWithStrings(List<String> header, List<double[]> rows,
                                        Map<Integer, String[]> stringColumns) {
        new ArrayTableData(header, rows, Collections.<Integer>emptySet(), stringColumns)
    }

    static double[] row(double... values) {
        return values
    }
}
