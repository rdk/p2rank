package cz.siret.prank.program.routines.predict.output

import groovy.transform.CompileStatic

/**
 * Minimal contract for tabular double data with named columns.
 * Implementations provide indexed access to rows for efficient export to CSV/Arrow formats.
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

}
