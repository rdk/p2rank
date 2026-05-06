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

    ArrayTableData(List<String> header, List<double[]> rows) {
        this(header, rows, Collections.<Integer>emptySet())
    }

    ArrayTableData(List<String> header, List<double[]> rows, Set<Integer> intColumnIndices) {
        this.header = header
        this.rows = rows
        this.intColumnIndices = intColumnIndices
    }

    @Override
    List<String> getHeader() { header }

    @Override
    int getRowCount() { rows.size() }

    @Override
    double[] getRow(int index) { rows[index] }

    @Override
    ColumnType getColumnType(int colIndex) {
        return intColumnIndices.contains(colIndex) ? ColumnType.INT : ColumnType.DOUBLE
    }

    // --- Factory methods for fluent test creation ---

    static ArrayTableData of(List<String> header, List<double[]> rows) {
        new ArrayTableData(header, rows)
    }

    static ArrayTableData ofWithInts(List<String> header, List<double[]> rows, Integer... intColIndices) {
        new ArrayTableData(header, rows, intColIndices.toList() as Set<Integer>)
    }

    static double[] row(double... values) {
        return values
    }
}
