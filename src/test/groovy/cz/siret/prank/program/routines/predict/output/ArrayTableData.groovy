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

    ArrayTableData(List<String> header, List<double[]> rows) {
        this.header = header
        this.rows = rows
    }

    @Override
    List<String> getHeader() { header }

    @Override
    int getRowCount() { rows.size() }

    @Override
    double[] getRow(int index) { rows[index] }

    // --- Factory methods for fluent test creation ---

    static ArrayTableData of(List<String> header, List<double[]> rows) {
        new ArrayTableData(header, rows)
    }

    static double[] row(double... values) {
        return values
    }
}
