package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
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

}
