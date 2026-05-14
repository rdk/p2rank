package cz.siret.prank.program.routines.analyze

import groovy.transform.CompileStatic

import java.util.concurrent.ConcurrentLinkedQueue

import static cz.siret.prank.utils.Formatter.format

/**
 * Collects structured rows of named values and produces CSV output and summary statistics.
 *
 * Columns are pre-registered at construction time. Row operations require no synchronization -
 * each Row is filled by a single thread, and only the final {@code rows.add()} is synchronized.
 *
 * Usage:
 * <pre>
 *   DataTable dt = new DataTable("protein",
 *       "n_chains", "n_residues", "chain_ids"
 *   )
 *   dt.newRow("1abc").put("n_chains", 3).put("n_residues", 450).put("chain_ids", "A B C")
 *   writeFile "out.csv", dt.toCsv()
 *   write dt.formatSummaryTable("Summary")
 * </pre>
 */
@CompileStatic
class DataTable {

    final String labelColumn
    private final String[] columns
    private final Map<String, Integer> columnIndex   // immutable after construction
    private final ConcurrentLinkedQueue<Row> rows = new ConcurrentLinkedQueue<>()  // lock-free

    DataTable(String labelColumn, String... columns) {
        this.labelColumn = labelColumn
        this.columns = columns
        Map<String, Integer> idx = new HashMap<>(columns.length * 2)
        for (int i = 0; i < columns.length; i++) {
            idx.put(columns[i], i)
        }
        this.columnIndex = Collections.unmodifiableMap(idx)
    }

    /**
     * Creates a new row and adds it to the table.
     * The returned Row can be filled via {@code put()} - no synchronization needed.
     */
    Row newRow(String label) {
        Row row = new Row(label, columns.length, columnIndex)
        rows.add(row)
        return row
    }

    /**
     * Materializes rows into an immutable sorted list.
     * Call after all rows have been added (i.e. after processItems completes).
     */
    private List<Row> materializedRows

    private List<Row> getRows() {
        if (materializedRows == null) {
            materializedRows = (rows.toList().toSorted { Row a, Row b -> a.label <=> b.label }) as List<Row>
        }
        return materializedRows
    }

    int size() {
        getRows().size()
    }

    List<Row> getRowsSorted() {
        getRows()
    }

    // ---- CSV output ----

    String toCsv() {
        StringBuilder sb = new StringBuilder()

        sb << csvCell(labelColumn)
        for (String col : columns) {
            sb << ", " << csvCell(col)
        }
        sb << "\n"

        for (Row row : getRowsSorted()) {
            sb << csvCell(row.label)
            for (int i = 0; i < columns.length; i++) {
                sb << ", "
                Object val = row.values[i]
                if (val != null) sb << csvCell(val.toString())
            }
            sb << "\n"
        }

        return sb.toString()
    }

    /**
     * Minimal CSV cell encoding (RFC 4180):
     * - If the cell contains comma, double-quote, CR, or LF, wrap it in double quotes
     *   and escape any inner double quotes by doubling them.
     * - Otherwise, return as-is.
     */
    private static String csvCell(String value) {
        if (value == null) return ""
        boolean needsQuoting = value.indexOf(',') >= 0 || value.indexOf('"') >= 0 ||
                value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0
        if (!needsQuoting) return value
        return '"' + value.replace('"', '""') + '"'
    }

    // ---- Summary stats ----

    /**
     * Count rows where the given column equals the given value.
     */
    int countWhere(String column, int value) {
        int idx = resolveIndex(column)
        int count = 0
        for (Row row : getRows()) {
            Object v = row.values[idx]
            if (v instanceof Number && ((Number) v).intValue() == value) {
                count++
            }
        }
        return count
    }

    /**
     * Returns indices of columns that have at least one numeric value.
     */
    private List<Integer> getNumericColumnIndices(Set<String> exclude = Collections.emptySet()) {
        List<Integer> result = new ArrayList<>()
        for (int i = 0; i < columns.length; i++) {
            if (exclude.contains(columns[i])) continue
            int ci = i
            if (getRows().any { Row row -> row.values[ci] instanceof Number }) {
                result.add(i)
            }
        }
        return result
    }

    /**
     * Extracts sorted numeric values for a given column index.
     */
    private List<Double> getNumericValuesSorted(int colIndex) {
        List<Row> allRows = getRows()
        List<Double> result = new ArrayList<>(allRows.size())
        for (Row row : allRows) {
            Object val = row.values[colIndex]
            if (val instanceof Number) {
                result.add(((Number) val).doubleValue())
            }
        }
        result.sort()
        return result
    }

    String formatSummaryTable(String title = "Dataset Summary", Map<String, Object> extraInfo = [:], Set<String> excludeFromSummary = Collections.emptySet(), String totalLabel = "Total entries:") {
        List<Integer> numColIndices = getNumericColumnIndices(excludeFromSummary)
        int n = size()

        StringBuilder table = new StringBuilder()
        table << "\n"
        table << "=== $title ===\n"
        table << "\n"
        table << String.format("  %-22s %d\n", totalLabel, n)

        for (Map.Entry<String, Object> entry : extraInfo.entrySet()) {
            table << String.format("  %-22s %s\n", entry.key, entry.value)
        }

        if (n > 0 && !numColIndices.isEmpty()) {
            table << "\n"
            table << String.format("  %-22s %10s %10s %10s %10s %10s\n", "column", "min", "max", "avg", "median", "sum")
            table << "  " << "-" * 77 << "\n"

            for (int ci : numColIndices) {
                List<Double> vals = getNumericValuesSorted(ci)
                if (vals.isEmpty()) continue

                double min = vals.first()
                double max = vals.last()
                double sum = vals.sum() as double
                double avg = sum / vals.size()
                double median = computeMedian(vals)

                if (isIntegerColumn(ci)) {
                    table << String.format("  %-22s %10d %10d %10s %10s %10d\n",
                            columns[ci], (long) min, (long) max, format(avg, 1), format(median, 1), (long) sum)
                } else {
                    table << String.format("  %-22s %10s %10s %10s %10s %10s\n",
                            columns[ci], format(min, 2), format(max, 2), format(avg, 2), format(median, 2), format(sum, 2))
                }
            }
        }

        table << "\n"
        return table.toString()
    }

    /**
     * Returns distinct values of a string column, in insertion order.
     */
    List<String> distinctValues(String column) {
        int idx = resolveIndex(column)
        Set<String> seen = new LinkedHashSet<>()
        for (Row row : getRows()) {
            Object val = row.values[idx]
            if (val != null) seen.add(val.toString())
        }
        return new ArrayList<>(seen)
    }

    /**
     * Returns a new DataTable containing only rows where the given column equals the given value.
     * The new table has the same columns (excluding the filter column).
     */
    DataTable filter(String filterColumn, String filterValue) {
        int filterIdx = resolveIndex(filterColumn)
        // Build new table without the filter column
        List<String> newCols = new ArrayList<>()
        for (String col : columns) {
            if (col != filterColumn) newCols.add(col)
        }
        DataTable result = new DataTable(labelColumn, newCols as String[])
        for (Row row : getRows()) {
            Object val = row.values[filterIdx]
            if (val != null && val.toString() == filterValue) {
                Row newRow = result.newRow(row.label)
                for (String col : newCols) {
                    Object v = row.values[resolveIndex(col)]
                    if (v instanceof Integer) newRow.put(col, (int) v)
                    else if (v instanceof Long) newRow.put(col, (long) v)
                    else if (v instanceof Double) newRow.put(col, (double) v)
                    else if (v instanceof String) newRow.put(col, (String) v)
                    else if (v instanceof Number) newRow.put(col, ((Number) v).doubleValue())
                }
            }
        }
        return result
    }

    /**
     * Produces a summary table with stats (count, min, max, avg, median) for selected numeric columns,
     * broken down by the values in the given groupBy column.
     */
    String formatGroupedSummaryTable(String groupByColumn, List<String> statColumns, String title = "Grouped Summary") {
        int groupIdx = resolveIndex(groupByColumn)
        List<Integer> statIndices = statColumns.collect { resolveIndex(it) }

        // Group rows by the groupBy column value (preserving insertion order)
        Map<String, List<Row>> groups = new LinkedHashMap<>()
        for (Row row : getRows()) {
            String key = row.values[groupIdx]?.toString() ?: ""
            groups.computeIfAbsent(key, { new ArrayList<>() }).add(row)
        }

        StringBuilder table = new StringBuilder()
        table << "\n"
        table << "=== $title ===\n"

        for (Map.Entry<String, List<Row>> entry : groups.entrySet()) {
            String groupName = entry.key
            List<Row> groupRows = entry.value

            table << "\n"
            table << "  $groupByColumn = $groupName  (n=${groupRows.size()})\n"
            table << String.format("  %-22s %10s %10s %10s %10s\n", "column", "min", "max", "avg", "median")
            table << "  " << "-" * 65 << "\n"

            for (int ci : statIndices) {
                List<Double> vals = new ArrayList<>()
                for (Row row : groupRows) {
                    Object val = row.values[ci]
                    if (val instanceof Number) {
                        double d = ((Number) val).doubleValue()
                        if (!Double.isNaN(d)) {
                            vals.add(d)
                        }
                    }
                }
                if (vals.isEmpty()) continue
                vals.sort()

                double min = vals.first()
                double max = vals.last()
                double sum = 0d
                for (double v : vals) { sum += v }
                double avg = sum / vals.size()
                double median = computeMedian(vals)

                table << String.format("  %-22s %10s %10s %10s %10s\n",
                        columns[ci], format(min, 3), format(max, 3), format(avg, 3), format(median, 3))
            }
        }

        table << "\n"
        return table.toString()
    }

    private boolean isIntegerColumn(int colIndex) {
        for (Row row : getRows()) {
            Object v = row.values[colIndex]
            if (v != null && !(v instanceof Integer) && !(v instanceof Long)) {
                return false
            }
        }
        return true
    }

    private int resolveIndex(String column) {
        Integer idx = columnIndex.get(column)
        if (idx == null) throw new IllegalArgumentException("Unknown column: $column")
        return idx
    }

    private static double computeMedian(List<Double> sorted) {
        int n = sorted.size()
        if (n % 2 == 1) {
            return sorted[n / 2]
        } else {
            return (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0d
        }
    }

    // ---- Row ----

    @CompileStatic
    static class Row {
        final String label
        final Object[] values
        private final Map<String, Integer> columnIndex

        Row(String label, int numColumns, Map<String, Integer> columnIndex) {
            this.label = label
            this.values = new Object[numColumns]
            this.columnIndex = columnIndex
        }

        Row put(String column, int value) {
            values[columnIndex.get(column)] = Integer.valueOf(value)
            return this
        }

        Row put(String column, long value) {
            values[columnIndex.get(column)] = Long.valueOf(value)
            return this
        }

        Row put(String column, double value) {
            values[columnIndex.get(column)] = Double.valueOf(value)
            return this
        }

        Row put(String column, String value) {
            values[columnIndex.get(column)] = value
            return this
        }

        Object get(String column) {
            values[columnIndex.get(column)]
        }
    }

}
