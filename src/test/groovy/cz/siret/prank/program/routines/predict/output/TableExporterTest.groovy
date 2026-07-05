package cz.siret.prank.program.routines.predict.output

import blue.strategic.parquet.ParquetReader
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import org.apache.arrow.memory.RootAllocator
import org.apache.arrow.vector.ipc.ArrowStreamReader
import org.apache.arrow.vector.types.pojo.ArrowType
import org.apache.parquet.schema.PrimitiveType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path
import java.util.zip.GZIPInputStream

import static cz.siret.prank.program.routines.predict.output.ArrayTableData.row
import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class TableExporterTest {

    @TempDir
    Path tempDir

    @Test
    void exportsCsvWithHeaderAndData() {
        def data = ArrayTableData.of(["a", "b"], [row(1.0d, 2.0d), row(3.0d, 4.0d)])
        def filepath = "$tempDir/test.csv"

        TableExporter.export(data, filepath, "csv")

        def lines = new File(filepath).readLines()
        assertEquals(3, lines.size())
        assertEquals("a,b", lines[0])
        assertTrue(lines[1].contains("1"))
        assertTrue(lines[1].contains("2"))
    }

    @Test
    void csvDoubleFormatterMatchesLegacyOutputContract() {
        // Pins the "0.#######" output shape the legacy DecimalFormat-based formatter
        // produced. The fast formatter rebuild swapped that for JDK Double.toString
        // (Schubfach since Java 19) plus pre-rounding; this test guards the shape
        // contract so the swap can't drift on a future JDK upgrade.
        //
        // Note: any future change to the bench-format contract (e.g. switching to
        // full round-trip representation) should update both this test and the
        // {@code formatDouble} javadoc together.
        def data = ArrayTableData.of(
                ["v"],
                [
                    row(0d),                  // exact zero — indicator-off fast path
                    row(1d),                  // exact one  — indicator-on fast path
                    row(0.5d),                // simple fraction, shortest form
                    row(5d),                  // integer-valued double — trailing ".0" must be stripped
                    row(1.0d / 3.0d),         // long fractional — must round to 7 places
                    row(-2.5d),               // negative value preserved verbatim
                    row(1.2345678d),          // exactly 7 places — no rounding effect
                    row(1.23456789d),         // 8 places — last digit must be rounded
                ])
        def filepath = "$tempDir/test_format.csv"
        TableExporter.export(data, filepath, "csv")

        def lines = new File(filepath).readLines()
        assertEquals(["v", "0", "1", "0.5", "5", "0.3333333", "-2.5", "1.2345678", "1.2345679"],
                lines, "CSV double-formatter output drifted from the legacy contract")
    }

    @Test
    void csvHandlesNaNAndInfinityWithoutSilentZero() {
        // The fast path uses Math.round(d * 1e7); on NaN that yields 0L and would
        // silently produce "0" in the output, masking a real bug upstream. The
        // formatter must short-circuit non-finite values to Double.toString's
        // own (locale-independent) spellings.
        def data = ArrayTableData.of(
                ["v"],
                [row(Double.NaN), row(Double.POSITIVE_INFINITY), row(Double.NEGATIVE_INFINITY)])
        def filepath = "$tempDir/test_nonfinite.csv"
        TableExporter.export(data, filepath, "csv")

        def lines = new File(filepath).readLines()
        assertEquals(["v", "NaN", "Infinity", "-Infinity"], lines,
                "non-finite doubles must round-trip through Double.toString, not silently become 0")
    }

    @Test
    void exportsCsvGzipCompressed() {
        def data = ArrayTableData.of(["col"], [row(1.5d)])
        def filepath = "$tempDir/test.csv.gz"

        TableExporter.export(data, filepath, "csv.gz")

        def file = new File(filepath)
        assertTrue(file.exists())
        def content = new GZIPInputStream(new FileInputStream(file)).text
        assertTrue(content.startsWith("col"))
    }

    @Test
    void exportsCsvZstdCompressed() {
        def data = ArrayTableData.of(["col"], [row(2.5d)])
        def filepath = "$tempDir/test.csv.zst"

        TableExporter.export(data, filepath, "csv.zst")

        def file = new File(filepath)
        assertTrue(file.exists())
        def content = Futils.inputStream(file.path).text
        assertTrue(content.startsWith("col"))
    }

    @Test
    void exportsArrowFormat() {
        def data = ArrayTableData.of(["x", "y"], [row(1.0d, 2.0d), row(3.0d, 4.0d)])
        def filepath = "$tempDir/test.arrow"

        TableExporter.export(data, filepath, "arrow")

        def file = new File(filepath)
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }

    @Test
    void exportsArrowGzipCompressed() {
        def data = ArrayTableData.of(["val"], [row(1.0d)])
        def filepath = "$tempDir/test.arrow.gz"

        TableExporter.export(data, filepath, "arrow.gz")

        def file = new File(filepath)
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }

    @Test
    void exportsArrowZstdCompressed() {
        def data = ArrayTableData.of(["val"], [row(1.0d)])
        def filepath = "$tempDir/test.arrow.zst"

        TableExporter.export(data, filepath, "arrow.zst")

        def file = new File(filepath)
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }

    @Test
    void throwsOnNullData() {
        assertThrows(IllegalArgumentException) {
            TableExporter.export(null, "$tempDir/test.csv", "csv")
        }
    }

    @Test
    void handlesEmptyTable() {
        def data = ArrayTableData.of(["a", "b"], [])
        def filepath = "$tempDir/empty.csv"

        TableExporter.export(data, filepath, "csv")

        def lines = new File(filepath).readLines()
        assertEquals(1, lines.size())  // Header only
        assertEquals("a,b", lines[0])
    }

    @Test
    void preservesNumericPrecision() {
        def data = ArrayTableData.of(["value"], [row(0.1234567d)])
        def filepath = "$tempDir/precision.csv"

        TableExporter.export(data, filepath, "csv")

        def content = new File(filepath).text
        assertTrue(content.contains("0.1234567"))
    }

    @Test
    void exportsParquetFormat() {
        def data = ArrayTableData.of(["x", "y"], [row(1.0d, 2.0d), row(3.0d, 4.0d)])
        def filepath = "$tempDir/test.parquet"

        TableExporter.export(data, filepath, "parquet")

        def file = new File(filepath)
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
        // Verify Parquet magic bytes (PAR1)
        def bytes = file.bytes
        assertEquals((byte)0x50, bytes[0])  // P
        assertEquals((byte)0x41, bytes[1])  // A
        assertEquals((byte)0x52, bytes[2])  // R
        assertEquals((byte)0x31, bytes[3])  // 1
    }

    @Test
    void parquetHandlesEmptyTable() {
        def data = ArrayTableData.of(["a", "b"], [])
        def filepath = "$tempDir/empty.parquet"

        TableExporter.export(data, filepath, "parquet")

        def file = new File(filepath)
        assertTrue(file.exists())
    }

    // --- INT column type tests ---

    @Test
    void csvIntColumnHasNoDecimals() {
        def data = ArrayTableData.ofWithInts(
                ["score", "pocket"],
                [row(0.5d, 3.0d), row(0.7d, 0.0d)],
                1)
        def filepath = "$tempDir/int.csv"

        TableExporter.export(data, filepath, "csv")

        def lines = new File(filepath).readLines()
        assertEquals("score,pocket", lines[0])
        assertEquals("0.5,3", lines[1])
        assertEquals("0.7,0", lines[2])
    }

    @Test
    void arrowIntColumnIsInt32() {
        def data = ArrayTableData.ofWithInts(
                ["score", "pocket"],
                [row(0.5d, 7.0d)],
                1)
        def filepath = "$tempDir/int.arrow"

        TableExporter.export(data, filepath, "arrow")

        new RootAllocator().withCloseable { allocator ->
            new FileInputStream(filepath).withCloseable { is ->
                new ArrowStreamReader(is, allocator).withCloseable { reader ->
                    reader.loadNextBatch()
                    def root = reader.vectorSchemaRoot
                    def fields = root.schema.fields
                    assertTrue(fields[0].type instanceof ArrowType.FloatingPoint, "score should be FloatingPoint")
                    assertTrue(fields[1].type instanceof ArrowType.Int, "pocket should be Int")
                    def intType = (ArrowType.Int) fields[1].type
                    assertEquals(32, intType.getBitWidth())
                    assertTrue(intType.getIsSigned())
                    assertEquals(7, root.getVector("pocket").getObject(0))
                }
            }
        }
    }

    @Test
    void intColumnRejectsNaN() {
        def data = ArrayTableData.ofWithInts(
                ["score", "pocket"],
                [row(0.5d, Double.NaN)],
                1)
        def err = assertThrows(ArithmeticException) {
            TableExporter.export(data, "$tempDir/bad.csv", "csv")
        }
        assertTrue(err.message.contains("pocket"),
                "exception should name the offending column; was: ${err.message}")
    }

    @Test
    void intColumnRejectsOverflow() {
        def data = ArrayTableData.ofWithInts(
                ["score", "pocket"],
                [row(0.5d, 1e12d)],   // 10^12 > Integer.MAX_VALUE (~2.15e9)
                1)
        def filepath = "$tempDir/big.parquet"
        assertThrows(ArithmeticException) {
            TableExporter.export(data, filepath, "parquet")
        }
        // The INT-range check must happen BEFORE the Parquet writer is opened: if it
        // fired mid-write, ParquetWriter.close() would throw while flushing and leak
        // the file handle, leaving a corrupt partial .parquet that can't be deleted on
        // Windows (breaking @TempDir cleanup and any real caller's retry). Asserting the
        // file was never created guards that on every platform, not just Windows.
        assertFalse(new File(filepath).exists(),
                "rejected export must not leave a partial .parquet on disk")
    }

    @Test
    void parquetIntColumnIsInt32() {
        def data = ArrayTableData.ofWithInts(
                ["score", "pocket"],
                [row(0.5d, 11.0d)],
                1)
        def filepath = "$tempDir/int.parquet"

        TableExporter.export(data, filepath, "parquet")

        def metadata = ParquetReader.readMetadata(new File(filepath))
        def schema = metadata.fileMetaData.schema
        assertEquals(PrimitiveType.PrimitiveTypeName.DOUBLE,
                schema.getType("score").asPrimitiveType().primitiveTypeName)
        assertEquals(PrimitiveType.PrimitiveTypeName.INT32,
                schema.getType("pocket").asPrimitiveType().primitiveTypeName)
    }

    // --- STRING column type tests ---

    /** Mixed schema used across the STRING tests: name (STRING) | rank (INT) | score (DOUBLE) */
    private static ArrayTableData mixedSchemaTable(List<String> names, List<Integer> ranks, List<Double> scores) {
        def rows = (0..<names.size()).collect { int i ->
            row(Double.NaN, ranks[i] as double, scores[i] as double)
        }
        def stringColumns = (Map<Integer, String[]>) [(0): names as String[]]
        return new ArrayTableData(
                ["name", "rank", "score"],
                rows,
                [1] as Set<Integer>,
                stringColumns)
    }

    @Test
    void csvWritesStringColumn() {
        def data = mixedSchemaTable(["pocket.1", "pocket.2"], [1, 2], [0.5d, 0.3d])
        def filepath = "$tempDir/strings.csv"

        TableExporter.export(data, filepath, "csv")

        def lines = new File(filepath).readLines()
        assertEquals("name,rank,score", lines[0])
        assertEquals("pocket.1,1,0.5", lines[1])
        assertEquals("pocket.2,2,0.3", lines[2])
    }

    @Test
    void csvQuotesValuesWithSpecialChars() {
        def commaName = 'has,comma'
        def quoteName = 'has"quote'
        def newlineName = "has\nnewline"
        def data = mixedSchemaTable([commaName, quoteName, newlineName], [1, 2, 3], [0.1d, 0.2d, 0.3d])
        def filepath = "$tempDir/strings_special.csv"

        TableExporter.export(data, filepath, "csv")

        def content = new File(filepath).text
        assertTrue(content.contains('"has,comma"'), "comma value must be quoted")
        assertTrue(content.contains('"has""quote"'), "quote value must be quoted + escaped")
        assertTrue(content.contains('"has\nnewline"'), "newline value must be quoted")
    }

    @Test
    void arrowWritesStringColumnAsUtf8() {
        def data = mixedSchemaTable(["alpha", "beta"], [1, 2], [0.5d, 0.6d])
        def filepath = "$tempDir/strings.arrow"

        TableExporter.export(data, filepath, "arrow")

        new RootAllocator().withCloseable { allocator ->
            new FileInputStream(filepath).withCloseable { is ->
                new ArrowStreamReader(is, allocator).withCloseable { reader ->
                    reader.loadNextBatch()
                    def root = reader.vectorSchemaRoot
                    def fields = root.schema.fields
                    assertTrue(fields[0].type instanceof ArrowType.Utf8, "name should be Utf8")
                    def vec = root.getVector("name")
                    assertEquals("alpha", vec.getObject(0).toString())
                    assertEquals("beta", vec.getObject(1).toString())
                }
            }
        }
    }

    @Test
    void parquetWritesStringColumnAsBinaryUtf8() {
        def data = mixedSchemaTable(["pocket.1"], [1], [0.9d])
        def filepath = "$tempDir/strings.parquet"

        TableExporter.export(data, filepath, "parquet")

        def metadata = ParquetReader.readMetadata(new File(filepath))
        def schema = metadata.fileMetaData.schema
        def nameType = schema.getType("name").asPrimitiveType()
        assertEquals(PrimitiveType.PrimitiveTypeName.BINARY, nameType.primitiveTypeName)
        // LogicalTypeAnnotation.stringType() round-trips
        assertNotNull(nameType.logicalTypeAnnotation)
        assertEquals("STRING", nameType.logicalTypeAnnotation.toString())
    }

    @Test
    void csvNumericOnlyTableIsUnchangedByStringRefactor() {
        // Regression: numeric-only schemas must produce identical output (no quoting,
        // no string lookups) since this is the existing SAS-points export path.
        def data = ArrayTableData.of(["x", "y"], [row(1.5d, 2.5d), row(3.5d, 4.5d)])
        def filepath = "$tempDir/numeric.csv"

        TableExporter.export(data, filepath, "csv")

        def lines = new File(filepath).readLines()
        assertEquals(["x,y", "1.5,2.5", "3.5,4.5"], lines)
    }

}
