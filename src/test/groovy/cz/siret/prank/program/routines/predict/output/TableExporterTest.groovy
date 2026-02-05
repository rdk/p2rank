package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path
import java.util.zip.GZIPInputStream

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class TableExporterTest {

    @TempDir
    Path tempDir

    @Test
    void exportsCsvWithHeaderAndData() {
        def data = table(["a", "b"], [row(1.0d, 2.0d), row(3.0d, 4.0d)])
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
        def data = table(["col"], [row(1.5d)])
        def filepath = "$tempDir/test.csv.gz"

        TableExporter.export(data, filepath, "csv.gz")

        def file = new File(filepath)
        assertTrue(file.exists())
        def content = new GZIPInputStream(new FileInputStream(file)).text
        assertTrue(content.startsWith("col"))
    }

    @Test
    void exportsCsvZstdCompressed() {
        def data = table(["col"], [row(2.5d)])
        def filepath = "$tempDir/test.csv.zst"

        TableExporter.export(data, filepath, "csv.zst")

        def file = new File(filepath)
        assertTrue(file.exists())
        def content = Futils.inputStream(file.path).text
        assertTrue(content.startsWith("col"))
    }

    @Test
    void exportsArrowFormat() {
        def data = table(["x", "y"], [row(1.0d, 2.0d), row(3.0d, 4.0d)])
        def filepath = "$tempDir/test.arrow"

        TableExporter.export(data, filepath, "arrow")

        def file = new File(filepath)
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }

    @Test
    void exportsArrowGzipCompressed() {
        def data = table(["val"], [row(1.0d)])
        def filepath = "$tempDir/test.arrow.gz"

        TableExporter.export(data, filepath, "arrow.gz")

        def file = new File(filepath)
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }

    @Test
    void exportsArrowZstdCompressed() {
        def data = table(["val"], [row(1.0d)])
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
        def data = table(["a", "b"], [])
        def filepath = "$tempDir/empty.csv"

        TableExporter.export(data, filepath, "csv")

        def lines = new File(filepath).readLines()
        assertEquals(1, lines.size())  // Header only
        assertEquals("a,b", lines[0])
    }

    @Test
    void preservesNumericPrecision() {
        def data = table(["value"], [row(0.1234567d)])
        def filepath = "$tempDir/precision.csv"

        TableExporter.export(data, filepath, "csv")

        def content = new File(filepath).text
        assertTrue(content.contains("0.1234567"))
    }

    // --- Helpers ---

    private static TableData table(List<String> header, List<double[]> rows) {
        new SimpleTableData(header, rows)
    }

    private static double[] row(double... values) {
        return values
    }

    @CompileStatic
    private static class SimpleTableData implements TableData {
        final List<String> header
        final List<double[]> rows

        SimpleTableData(List<String> header, List<double[]> rows) {
            this.header = header
            this.rows = rows
        }

        @Override
        List<String> getHeader() { header }

        @Override
        int getRowCount() { rows.size() }

        @Override
        double[] getRow(int index) { rows[index] }
    }

}
