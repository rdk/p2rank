package cz.siret.prank.program.routines.predict.output

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.arrow.memory.RootAllocator
import org.apache.arrow.vector.Float8Vector
import org.apache.arrow.vector.VectorSchemaRoot
import org.apache.arrow.vector.ipc.ArrowStreamWriter
import org.apache.arrow.vector.types.FloatingPointPrecision
import org.apache.arrow.vector.types.pojo.ArrowType
import org.apache.arrow.vector.types.pojo.Field
import org.apache.arrow.vector.types.pojo.Schema
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream

import java.nio.channels.Channels
import java.util.zip.GZIPOutputStream

import static cz.siret.prank.utils.Formatter.format

/**
 * Exports tabular double data to CSV or Arrow format with optional compression.
 *
 * Supported format strings: csv, csv.gz, csv.zst, arrow, arrow.gz, arrow.zst
 */
@Slf4j
@CompileStatic
class TableExporter {

    /** Supported base formats */
    enum Format { CSV, ARROW }

    /** Supported compression methods */
    enum Compression { NONE, GZIP, ZSTD }

    private static final int BUFFER_SIZE = 65536

    private TableExporter() {}

    /**
     * Export table data to file.
     *
     * @param data      the table data to export
     * @param filepath  output file path
     * @param format    format string: "csv", "csv.gz", "csv.zst", "arrow", "arrow.gz", "arrow.zst"
     */
    static void export(TableData data, String filepath, String format) {
        if (data == null) {
            throw new IllegalArgumentException("TableData cannot be null")
        }

        Format baseFormat = parseBaseFormat(format)
        Compression compression = parseCompression(format)

        if (baseFormat == Format.ARROW) {
            writeArrow(data, filepath, compression)
        } else {
            writeCsv(data, filepath, compression)
        }
    }

    // --- Format Parsing ---

    private static Format parseBaseFormat(String format) {
        if (format == null) return Format.CSV
        String lower = format.toLowerCase()
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
            for (int c = 0; c < header.size(); c++) {
                if (c > 0) writer.print(",")
                writer.print(header.get(c))
            }
            writer.println()

            // Data rows
            int rowCount = data.getRowCount()
            for (int i = 0; i < rowCount; i++) {
                double[] row = data.getRow(i)
                for (int c = 0; c < row.length; c++) {
                    if (c > 0) writer.print(",")
                    writer.print(formatDouble(row[c]))
                }
                writer.println()
            }
            writer.flush()
        }
    }

    // --- Arrow Writer (IPC Streaming Format) ---

    private static void writeArrow(TableData data, String filepath, Compression compression) {
        // Streaming format doesn't require seeking, so we can write directly to any output stream
        createOutputStream(filepath, compression).withCloseable { OutputStream out ->
            new RootAllocator().withCloseable { allocator ->
                VectorSchemaRoot.create(buildSchema(data.getHeader()), allocator).withCloseable { root ->
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

    private static Schema buildSchema(List<String> header) {
        List<Field> fields = header.collect { String name ->
            Field.nullable(name, new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE))
        }
        return new Schema(fields)
    }

    private static void populateVectors(VectorSchemaRoot root, TableData data) {
        root.allocateNew()
        List<String> header = data.getHeader()
        int rowCount = data.getRowCount()
        int colCount = header.size()

        // Get all vectors
        List<Float8Vector> vectors = header.collect { String name ->
            (Float8Vector) root.getVector(name)
        }

        // Populate row by row
        for (int i = 0; i < rowCount; i++) {
            double[] row = data.getRow(i)
            for (int c = 0; c < colCount; c++) {
                vectors.get(c).setSafe(i, row[c])
            }
        }
        root.setRowCount(rowCount)
    }

    // --- I/O Helpers ---

    private static OutputStream createOutputStream(String filepath, Compression compression) {
        OutputStream base = new BufferedOutputStream(new FileOutputStream(filepath), BUFFER_SIZE)
        try {
            switch (compression) {
                case Compression.GZIP:
                    return new GZIPOutputStream(base, BUFFER_SIZE)
                case Compression.ZSTD:
                    return new ZstdCompressorOutputStream(base)
                default:
                    return base
            }
        } catch (Exception e) {
            base.close()
            throw e
        }
    }

    private static String formatDouble(double d) {
        return format(d, 7)
    }

}
