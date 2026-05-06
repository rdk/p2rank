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

}
