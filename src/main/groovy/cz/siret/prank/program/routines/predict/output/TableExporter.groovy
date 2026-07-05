package cz.siret.prank.program.routines.predict.output

import blue.strategic.parquet.Dehydrator
import blue.strategic.parquet.ParquetWriter
import blue.strategic.parquet.ValueWriter
import cz.siret.prank.program.routines.predict.output.TableData.ColumnType
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.arrow.memory.RootAllocator
import org.apache.arrow.vector.Float8Vector
import org.apache.arrow.vector.IntVector
import org.apache.arrow.vector.VarCharVector
import org.apache.arrow.vector.VectorSchemaRoot
import org.apache.arrow.vector.ipc.ArrowStreamWriter
import org.apache.arrow.vector.types.FloatingPointPrecision
import org.apache.arrow.vector.types.pojo.ArrowType
import org.apache.arrow.vector.types.pojo.Field
import org.apache.arrow.vector.types.pojo.Schema
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import org.apache.parquet.schema.LogicalTypeAnnotation
import org.apache.parquet.schema.MessageType
import org.apache.parquet.schema.PrimitiveType
import org.apache.parquet.schema.Types

import java.nio.channels.Channels
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream

/**
 * Exports tabular double data to CSV, Arrow, or Parquet format with optional compression.
 *
 * Supported format strings:
 *   CSV: csv, csv.gz, csv.zst
 *   Arrow: arrow, arrow.gz, arrow.zst
 *   Parquet: parquet (uses SNAPPY compression internally)
 */
@Slf4j
@CompileStatic
class TableExporter {

    /** Supported base formats */
    enum Format { CSV, ARROW, PARQUET }

    /** Supported compression methods (for CSV and Arrow) */
    enum Compression { NONE, GZIP, ZSTD }

    private static final int BUFFER_SIZE = 65536

    /** GZIP compression level (1-9, where 1=fastest, 9=best compression, 6=default) */
    private static final int GZIP_LEVEL = Deflater.DEFAULT_COMPRESSION

    /** Zstd compression level (1-22, where 1=fastest, 22=best compression, 3=default) */
    private static final int ZSTD_LEVEL = 16

    /** Decimal places for formatting doubles in CSV output. Doubles are pre-rounded
     *  to this precision before {@link Double#toString} formats the shortest
     *  round-trip representation, matching the legacy {@code DecimalFormat("0.#######")}
     *  output contract. */
    private static final int CSV_DECIMAL_PLACES = 7
    private static final double CSV_ROUND_SCALE = 1e7d

    private TableExporter() {}

    /**
     * Export table data to file.
     *
     * @param data      the table data to export
     * @param filepath  output file path
     * @param format    format string: "csv", "csv.gz", "csv.zst", "arrow", "arrow.gz", "arrow.zst", "parquet"
     */
    static void export(TableData data, String filepath, String format) {
        if (data == null) {
            throw new IllegalArgumentException("TableData cannot be null")
        }

        Format baseFormat = parseBaseFormat(format)

        switch (baseFormat) {
            case Format.PARQUET:
                writeParquet(data, filepath)
                break
            case Format.ARROW:
                Compression compression = parseCompression(format)
                writeArrow(data, filepath, compression)
                break
            default:
                Compression compression = parseCompression(format)
                writeCsv(data, filepath, compression)
        }
    }

    // --- Format Parsing ---

    private static Format parseBaseFormat(String format) {
        if (format == null) return Format.CSV
        String lower = format.toLowerCase()
        if (lower.startsWith("parquet")) return Format.PARQUET
        if (lower.startsWith("arrow")) return Format.ARROW
        if (lower.startsWith("csv")) return Format.CSV
        log.warn("Unknown format '{}', falling back to CSV", format)
        return Format.CSV
    }

    private static Compression parseCompression(String format) {
        if (format == null) return Compression.NONE
        if (format.endsWith(".gz")) return Compression.GZIP
        if (format.endsWith(".zst")) return Compression.ZSTD
        return Compression.NONE
    }

    // --- CSV Writer ---

    private static void writeCsv(TableData data, String filepath, Compression compression) {
        // try-with-resources instead of Groovy's withCloseable {} so the body is
        // statically compiled — the row-write loop is the hot path and a closure
        // wrapper shows up under its own _closure1.doCall stack frame in JFR.
        try (OutputStream out = createOutputStream(filepath, compression)) {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(out))

            // Header
            List<String> header = data.getHeader()
            int colCount = header.size()
            for (int c = 0; c < colCount; c++) {
                if (c > 0) writer.print(",")
                writer.print(header.get(c))
            }
            writer.print("\n")  // Explicit newline for cross-platform consistency

            // Cache column types so we don't dispatch per row
            ColumnType[] types = new ColumnType[colCount]
            for (int c = 0; c < colCount; c++) {
                types[c] = data.getColumnType(c)
            }

            // Data rows
            int rowCount = data.getRowCount()
            for (int i = 0; i < rowCount; i++) {
                double[] row = data.getRow(i)
                for (int c = 0; c < colCount; c++) {
                    if (c > 0) writer.print(",")
                    switch (types[c]) {
                        case ColumnType.STRING:
                            writer.print(quoteCsv(data.getString(i, c)))
                            break
                        case ColumnType.INT:
                            writer.print(Integer.toString(toIntStrict(row[c], header.get(c))))
                            break
                        default:
                            writer.print(formatDouble(row[c]))
                    }
                }
                writer.print("\n")
            }
            writer.flush()
        }
    }

    /**
     * RFC 4180 CSV quoting: wrap in double quotes and escape internal quotes by doubling them
     * if the value contains a comma, quote, CR, or LF. Otherwise return as-is.
     */
    private static String quoteCsv(String value) {
        if (value == null) return ""
        if (value.contains(",") || value.contains('"') || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace('"', '""') + '"'
        }
        return value
    }

    // --- Arrow Writer (IPC Streaming Format) ---

    private static void writeArrow(TableData data, String filepath, Compression compression) {
        // Streaming format doesn't require seeking, so we can write directly to any output stream
        try (OutputStream out = createOutputStream(filepath, compression);
             RootAllocator allocator = new RootAllocator();
             VectorSchemaRoot root = VectorSchemaRoot.create(buildSchema(data), allocator)) {
            populateVectors(root, data)
            try (ArrowStreamWriter writer = new ArrowStreamWriter(root, null, Channels.newChannel(out))) {
                writer.start()
                writer.writeBatch()
                writer.end()
            }
        }
    }

    private static Schema buildSchema(TableData data) {
        List<String> header = data.getHeader()
        List<Field> fields = new ArrayList<>(header.size())
        for (int c = 0; c < header.size(); c++) {
            ArrowType type
            switch (data.getColumnType(c)) {
                case ColumnType.INT:
                    type = new ArrowType.Int(32, true)
                    break
                case ColumnType.STRING:
                    type = new ArrowType.Utf8()
                    break
                default:
                    type = new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)
            }
            fields.add(Field.nullable(header.get(c), type))
        }
        return new Schema(fields)
    }

    private static void populateVectors(VectorSchemaRoot root, TableData data) {
        root.allocateNew()
        List<String> header = data.getHeader()
        int rowCount = data.getRowCount()
        int colCount = header.size()

        // Populate column by column (more efficient for columnar format)
        for (int c = 0; c < colCount; c++) {
            switch (data.getColumnType(c)) {
                case ColumnType.STRING:
                    VarCharVector strVector = (VarCharVector) root.getVector(header.get(c))
                    for (int i = 0; i < rowCount; i++) {
                        strVector.setSafe(i, data.getString(i, c).getBytes(StandardCharsets.UTF_8))
                    }
                    break
                case ColumnType.INT:
                    double[] intColumn = data.getColumn(c)
                    IntVector intVector = (IntVector) root.getVector(header.get(c))
                    String intColName = header.get(c)
                    for (int i = 0; i < rowCount; i++) {
                        intVector.setSafe(i, toIntStrict(intColumn[i], intColName))
                    }
                    break
                default:
                    double[] dblColumn = data.getColumn(c)
                    Float8Vector dblVector = (Float8Vector) root.getVector(header.get(c))
                    for (int i = 0; i < rowCount; i++) {
                        dblVector.setSafe(i, dblColumn[i])
                    }
            }
        }
        root.setRowCount(rowCount)
    }

    // --- Parquet Writer (uses SNAPPY compression) ---

    private static void writeParquet(TableData data, String filepath) {
        List<String> header = data.getHeader()
        ColumnType[] types = new ColumnType[header.size()]
        for (int c = 0; c < header.size(); c++) {
            types[c] = data.getColumnType(c)
        }

        // Reject non-representable INT values BEFORE opening the writer. If toIntStrict
        // throws from inside the dehydrator (i.e. mid-write), the try-with-resources
        // calls ParquetWriter.close(), which flushes the half-written row group and
        // writes the footer — and that throws before reaching the underlying stream's
        // close(), leaking the output file handle. On Windows an open file cannot be
        // deleted, so callers (and JUnit's @TempDir) can neither clean up nor overwrite
        // the corrupt partial .parquet. Validating up-front keeps the failure clean and
        // leaves no partial file on disk. (CSV/Arrow close their streams cleanly on the
        // same failure, so this guard is only needed on the Parquet path.)
        assertIntColumnsRepresentable(data, header, types)

        MessageType schema = buildParquetSchema(header, types)
        File outputFile = new File(filepath)

        Dehydrator<Integer> dehydrator = new RowDehydrator(data, header, types)

        try (ParquetWriter<Integer> writer = ParquetWriter.writeFile(schema, outputFile, dehydrator)) {
            int rowCount = data.getRowCount()
            for (int i = 0; i < rowCount; i++) {
                writer.write(Integer.valueOf(i))
            }
        }
    }

    private static MessageType buildParquetSchema(List<String> header, ColumnType[] types) {
        Types.MessageTypeBuilder builder = Types.buildMessage()
        for (int c = 0; c < header.size(); c++) {
            switch (types[c]) {
                case ColumnType.INT:
                    builder.required(PrimitiveType.PrimitiveTypeName.INT32).named(header.get(c))
                    break
                case ColumnType.STRING:
                    builder.required(PrimitiveType.PrimitiveTypeName.BINARY)
                            .as(LogicalTypeAnnotation.stringType())
                            .named(header.get(c))
                    break
                default:
                    builder.required(PrimitiveType.PrimitiveTypeName.DOUBLE).named(header.get(c))
            }
        }
        return builder.named("table")
    }

    @CompileStatic
    private static class RowDehydrator implements Dehydrator<Integer> {
        private final TableData data
        private final List<String> header
        private final ColumnType[] types

        RowDehydrator(TableData data, List<String> header, ColumnType[] types) {
            this.data = data
            this.header = header
            this.types = types
        }

        @Override
        void dehydrate(Integer rowIndex, ValueWriter valueWriter) {
            // For numeric columns we still want the cheap double[] path; only fetch when needed.
            double[] row = null
            for (int i = 0; i < header.size(); i++) {
                switch (types[i]) {
                    case ColumnType.STRING:
                        valueWriter.write(header.get(i), data.getString(rowIndex, i))
                        break
                    case ColumnType.INT:
                        if (row == null) row = data.getRow(rowIndex)
                        valueWriter.write(header.get(i),
                                Integer.valueOf(toIntStrict(row[i], header.get(i))))
                        break
                    default:
                        if (row == null) row = data.getRow(rowIndex)
                        valueWriter.write(header.get(i), row[i])
                }
            }
        }
    }

    // --- I/O Helpers ---

    private static OutputStream createOutputStream(String filepath, Compression compression) {
        OutputStream base = new BufferedOutputStream(new FileOutputStream(filepath), BUFFER_SIZE)
        try {
            switch (compression) {
                case Compression.GZIP:
                    return new ConfigurableGzipOutputStream(base, BUFFER_SIZE, GZIP_LEVEL)
                case Compression.ZSTD:
                    return new ZstdCompressorOutputStream(base, ZSTD_LEVEL)
                default:
                    return base
            }
        } catch (Exception e) {
            base.close()
            throw e
        }
    }

    /**
     * GZIPOutputStream with configurable compression level.
     */
    private static class ConfigurableGzipOutputStream extends GZIPOutputStream {
        ConfigurableGzipOutputStream(OutputStream out, int bufferSize, int level) throws IOException {
            super(out, bufferSize)
            this.@def.setLevel(level)  // 'def' is a Groovy keyword, use @ to access field directly
        }
    }

    /**
     * Fast CSV-cell formatter for {@code double} values. Was a hot path —
     * {@code DecimalFormat.format} appeared at ~2.2 % of total wall on the
     * pocket-grid bench — so this delegates to the JDK's Schubfach-based
     * {@link Double#toString} (the JDK-built-in equivalent of Ryu since
     * Java 19), with pre-rounding to {@link #CSV_DECIMAL_PLACES} so output
     * matches the legacy {@code DecimalFormat("0.#######")} contract:
     *
     * <ul>
     *   <li>integer-valued doubles render without a trailing {@code .0}
     *       ({@code 1.0} → {@code "1"}, not {@code "1.0"})</li>
     *   <li>trailing zeros after the decimal point are stripped
     *       (already given by {@code Double.toString}'s shortest form)</li>
     *   <li>at most 7 digits after the decimal point
     *       (without pre-rounding, {@code 1.0 / 3.0} would expand from
     *       {@code "0.3333333"} to the full 16-digit round-trip form)</li>
     * </ul>
     *
     * <p>NaN and infinities use {@code Double.toString}'s {@code "NaN"} /
     * {@code "Infinity"} / {@code "-Infinity"} spellings — locale-independent
     * unlike DecimalFormat's defaults.
     */
    private static String formatDouble(double d) {
        // Indicator descriptors (volsite hard) are 0/1 on every row; making this
        // the first branch wins on the common case for the dataset that motivated
        // the optimization. Negative-zero compares equal to positive-zero so it
        // also takes this path.
        if (d == 0d) return "0"
        if (d == 1d) return "1"

        // Math.round on (NaN * scale) yields 0L and silently produces "0";
        // catch non-finite values BEFORE pre-rounding so they round-trip
        // through Double.toString cleanly.
        if (!Double.isFinite(d)) return Double.toString(d)

        // Pre-round to 7 decimal places. The (d * 1e7) intermediate stays
        // within long range for any value with |d| < ~9.2e8, which covers all
        // descriptor and coordinate outputs by orders of magnitude. Above that
        // we'd silently saturate Math.round — fall back to the un-rounded
        // toString to avoid losing the value entirely.
        if (d > 9.2e8d || d < -9.2e8d) return Double.toString(d)
        double rounded = Math.round(d * CSV_ROUND_SCALE) / CSV_ROUND_SCALE
        String s = Double.toString(rounded)

        // Double.toString always emits at least one digit after the decimal
        // point, so integer-valued rounds come back as "5.0", "12.0", etc.
        // Strip the trailing ".0" to match DecimalFormat("0.#######").
        int len = s.length()
        if (len >= 2 && s.charAt(len - 1) == ('0' as char) && s.charAt(len - 2) == ('.' as char)) {
            return s.substring(0, len - 2)
        }
        return s
    }

    /**
     * Narrow an INT-column {@code double} value to {@code int}. Enforces the
     * "INT columns must fit in i32" contract documented on
     * {@code PocketDescriptor} and {@code PocketGridPointDescriptor}: rejects
     * NaN, infinities, and any finite value outside the {@code int} range.
     * Without this, NaN silently became 0 and overflow wrapped silently to
     * {@code Integer.MAX_VALUE} / {@code MIN_VALUE} — the export docs now warn
     * about that quirk but the code didn't enforce it.
     */
    private static int toIntStrict(double v, String columnName) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new ArithmeticException(
                    "INT column '${columnName}' got non-finite value: ${v}")
        }
        long asLong = (long) v
        return Math.toIntExact(asLong)
    }

    /**
     * Eagerly run {@link #toIntStrict} over every INT-column value so an out-of-range
     * or non-finite value is rejected before any output file is opened. See
     * {@link #writeParquet} for why the Parquet path cannot tolerate the exception
     * being thrown mid-write. INT columns are sparse (typically a single rank column),
     * so this extra pass is negligible next to the write itself.
     */
    private static void assertIntColumnsRepresentable(TableData data, List<String> header, ColumnType[] types) {
        int rowCount = data.getRowCount()
        for (int c = 0; c < types.length; c++) {
            if (types[c] == ColumnType.INT) {
                double[] column = data.getColumn(c)
                String columnName = header.get(c)
                for (int i = 0; i < rowCount; i++) {
                    toIntStrict(column[i], columnName)
                }
            }
        }
    }

}
