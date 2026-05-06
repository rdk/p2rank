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
import org.apache.arrow.vector.VectorSchemaRoot
import org.apache.arrow.vector.ipc.ArrowStreamWriter
import org.apache.arrow.vector.types.FloatingPointPrecision
import org.apache.arrow.vector.types.pojo.ArrowType
import org.apache.arrow.vector.types.pojo.Field
import org.apache.arrow.vector.types.pojo.Schema
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import org.apache.parquet.schema.MessageType
import org.apache.parquet.schema.PrimitiveType
import org.apache.parquet.schema.Types

import java.nio.channels.Channels
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream

import static cz.siret.prank.utils.Formatter.format

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

    /** Decimal places for formatting doubles in CSV output */
    private static final int CSV_DECIMAL_PLACES = 7

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
        createOutputStream(filepath, compression).withCloseable { OutputStream out ->
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
                for (int c = 0; c < row.length; c++) {
                    if (c > 0) writer.print(",")
                    if (types[c] == ColumnType.INT) {
                        writer.print(Long.toString((long) row[c]))
                    } else {
                        writer.print(formatDouble(row[c]))
                    }
                }
                writer.print("\n")
            }
            writer.flush()
        }
    }

    // --- Arrow Writer (IPC Streaming Format) ---

    private static void writeArrow(TableData data, String filepath, Compression compression) {
        // Streaming format doesn't require seeking, so we can write directly to any output stream
        createOutputStream(filepath, compression).withCloseable { OutputStream out ->
            new RootAllocator().withCloseable { allocator ->
                VectorSchemaRoot.create(buildSchema(data), allocator).withCloseable { root ->
                    populateVectors(root, data)
                    new ArrowStreamWriter(root, null, Channels.newChannel(out)).withCloseable { writer ->
                        writer.start()
                        writer.writeBatch()
                        writer.end()
                    }
                }
            }
        }
    }

    private static Schema buildSchema(TableData data) {
        List<String> header = data.getHeader()
        List<Field> fields = new ArrayList<>(header.size())
        for (int c = 0; c < header.size(); c++) {
            ArrowType type = (data.getColumnType(c) == ColumnType.INT)
                    ? new ArrowType.Int(32, true)
                    : new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)
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
            double[] column = data.getColumn(c)
            if (data.getColumnType(c) == ColumnType.INT) {
                IntVector vector = (IntVector) root.getVector(header.get(c))
                for (int i = 0; i < rowCount; i++) {
                    vector.setSafe(i, (int) column[i])
                }
            } else {
                Float8Vector vector = (Float8Vector) root.getVector(header.get(c))
                for (int i = 0; i < rowCount; i++) {
                    vector.setSafe(i, column[i])
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

        MessageType schema = buildParquetSchema(header, types)
        File outputFile = new File(filepath)

        Dehydrator<double[]> dehydrator = new RowDehydrator(header, types)

        ParquetWriter.writeFile(schema, outputFile, dehydrator).withCloseable { ParquetWriter<double[]> writer ->
            int rowCount = data.getRowCount()
            for (int i = 0; i < rowCount; i++) {
                writer.write(data.getRow(i))
            }
        }
    }

    private static MessageType buildParquetSchema(List<String> header, ColumnType[] types) {
        Types.MessageTypeBuilder builder = Types.buildMessage()
        for (int c = 0; c < header.size(); c++) {
            PrimitiveType.PrimitiveTypeName primitive = (types[c] == ColumnType.INT)
                    ? PrimitiveType.PrimitiveTypeName.INT32
                    : PrimitiveType.PrimitiveTypeName.DOUBLE
            builder.required(primitive).named(header.get(c))
        }
        return builder.named("table")
    }

    @CompileStatic
    private static class RowDehydrator implements Dehydrator<double[]> {
        private final List<String> header
        private final ColumnType[] types

        RowDehydrator(List<String> header, ColumnType[] types) {
            this.header = header
            this.types = types
        }

        @Override
        void dehydrate(double[] row, ValueWriter valueWriter) {
            for (int i = 0; i < header.size(); i++) {
                if (types[i] == ColumnType.INT) {
                    valueWriter.write(header.get(i), Integer.valueOf((int) row[i]))
                } else {
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

    private static String formatDouble(double d) {
        return format(d, CSV_DECIMAL_PLACES)
    }

}
