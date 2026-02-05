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

}
