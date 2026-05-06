package cz.siret.prank.program.routines.predict.output

import groovy.transform.CompileStatic

/**
 * Minimal contract for tabular double data with named columns.
 * Implementations provide indexed access to rows for efficient export to CSV/Arrow/Parquet formats.
 *
 * For row-oriented formats (CSV), use {@link #getRow(int)}.
 * For columnar formats (Arrow, Parquet), use {@link #getColumn(int)} for better performance.
 */
@CompileStatic
interface TableData {

    /** Logical type for a column. INT values are still stored as double in getRow/getColumn but are emitted as integers by writers. */
    enum ColumnType { DOUBLE, INT }

    /** Column names */
    List<String> getHeader()

    /** Number of data rows */
    int getRowCount()

    /**
     * Get row at index. Returned array length must equal header size.
     * @param index 0-based row index
     * @return array of values for this row
     */
    double[] getRow(int index)

    /**
     * Get column at index. Returned array length must equal row count.
     * Default implementation iterates rows - override for better performance.
     * @param colIndex 0-based column index
     * @return array of values for this column
     */
    default double[] getColumn(int colIndex) {
        int rowCount = getRowCount()
        double[] column = new double[rowCount]
        for (int i = 0; i < rowCount; i++) {
            column[i] = getRow(i)[colIndex]
        }
        return column
    }

    /**
     * Logical type of column at index. Default is DOUBLE; override to mark integer columns
     * so writers can emit them as native integer types (CSV without decimals, Arrow Int32, Parquet INT32).
     */
    default ColumnType getColumnType(int colIndex) {
        return ColumnType.DOUBLE
    }

}
