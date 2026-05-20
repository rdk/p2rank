package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.collectors.DoubleVector
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.AtomImpl
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import java.nio.file.Path
import java.util.zip.GZIPInputStream

import static org.junit.jupiter.api.Assertions.*

@Isolated
@ResourceLock("Params")
@CompileStatic
class PointsExporterTest {

    @TempDir
    Path tempDir

    static boolean savedExportPoints
    static String savedExportPointsFormat

    @BeforeAll
    static void snapshotParams() {
        savedExportPoints = Params.inst.export_points
        savedExportPointsFormat = Params.inst.export_points_format
    }

    @AfterAll
    static void restoreParams() {
        Params.inst.export_points = savedExportPoints
        Params.inst.export_points_format = savedExportPointsFormat
    }

    @BeforeEach
    void setUp() {
        Params.inst.export_points = true
        Params.inst.export_points_format = "csv"
    }

    @Test
    void writesCsvWithHeaderAndDataRows() {
        def data = exportData(
            [point(1, 2, 3, 0.5), point(4, 5, 6, 0.8)],
            [vector(0.1, 0.2), vector(0.3, 0.4)],
            ["feat1", "feat2"]
        )

        PointsExporter.exportPoints(data, tempDir.toString(), "test")

        def lines = outputFile("test").readLines()
        assertEquals(3, lines.size())
        assertEquals("x,y,z,score,pocket,feat1,feat2", lines[0])
    }

    @Test
    void writesCoordinatesScoresAndFeatures() {
        def data = exportData(
            [point(1.5, 2.5, 3.5, 0.75)],
            [vector(0.123, 0.456)],
            ["f1", "f2"]
        )

        PointsExporter.exportPoints(data, tempDir.toString(), "test")

        def row = outputFile("test").readLines()[1]
        def values = row.split(",")

        assertEquals(7, values.length)
        assertTrue(values[0].contains("1.5"))
        assertTrue(values[1].contains("2.5"))
        assertTrue(values[2].contains("3.5"))
        assertTrue(values[3].contains("0.75"))
        assertEquals("0", values[4])  // pocket: default 0 = unassigned
        assertTrue(values[5].contains("0.123"))
        assertTrue(values[6].contains("0.456"))
    }

    @Test
    void skipsExportWhenDisabled() {
        Params.inst.export_points = false

        PointsExporter.tryExportPoints(exportData([point(0,0,0,0)], [vector(1.0d)], ["f"]), tempDir.toString(), "disabled")

        assertFalse(outputFile("disabled").exists())
    }

    @Test
    void skipsExportWhenDataIsNull() {
        PointsExporter.tryExportPoints(null, tempDir.toString(), "nulldata")

        assertFalse(outputFile("nulldata").exists())
    }

    @Test
    void supportsGzipCompression() {
        Params.inst.export_points_format = "csv.gz"
        def data = exportData([point(1, 2, 3, 0.5)], [vector(0.1)], ["f1"])

        PointsExporter.exportPoints(data, tempDir.toString(), "compressed")

        def gzFile = new File("$tempDir/compressed_points.csv.gz")
        assertTrue(gzFile.exists())

        def content = new GZIPInputStream(new FileInputStream(gzFile)).text
        assertTrue(content.startsWith("x,y,z,score,pocket,f1"))
    }

    @Test
    void supportsZstdCompression() {
        Params.inst.export_points_format = "csv.zst"
        def data = exportData([point(1, 2, 3, 0.5)], [vector(0.1)], ["f1"])

        PointsExporter.exportPoints(data, tempDir.toString(), "zstd")

        def zstFile = new File("$tempDir/zstd_points.csv.zst")
        assertTrue(zstFile.exists())

        String content = Futils.inputStream(zstFile.path).text
        assertTrue(content.startsWith("x,y,z,score,pocket,f1"))
    }

    @Test
    void fallsBackToCsvForUnknownFormat() {
        Params.inst.export_points_format = "xyz123"
        def data = exportData([point(1, 2, 3, 0.5)], [vector(0.1)], ["f1"])

        PointsExporter.exportPoints(data, tempDir.toString(), "fallback")

        // Unknown format gets the specified extension but CSV content
        def file = new File("$tempDir/fallback_points.xyz123")
        assertTrue(file.exists())
        def content = file.text
        assertTrue(content.startsWith("x,y,z,score,pocket,f1"))
    }

    @Test
    void supportsParquetFormat() {
        Params.inst.export_points_format = "parquet"
        def data = exportData([point(1, 2, 3, 0.5)], [vector(0.1, 0.2)], ["f1", "f2"])

        PointsExporter.exportPoints(data, tempDir.toString(), "parquet_test")

        def parquetFile = new File("$tempDir/parquet_test_points.parquet")
        assertTrue(parquetFile.exists())
        assertTrue(parquetFile.length() > 0)
        // Verify Parquet magic bytes (PAR1)
        def bytes = parquetFile.bytes
        assertEquals((byte)0x50, bytes[0])  // P
        assertEquals((byte)0x41, bytes[1])  // A
        assertEquals((byte)0x52, bytes[2])  // R
        assertEquals((byte)0x31, bytes[3])  // 1
    }

    @Test
    void supportsArrowFormat() {
        Params.inst.export_points_format = "arrow"
        def data = exportData([point(1, 2, 3, 0.5)], [vector(0.1, 0.2)], ["f1", "f2"])

        PointsExporter.exportPoints(data, tempDir.toString(), "arrow_test")

        def arrowFile = new File("$tempDir/arrow_test_points.arrow")
        assertTrue(arrowFile.exists())
        assertTrue(arrowFile.length() > 0)
    }

    @Test
    void supportsCompressedArrowGzip() {
        Params.inst.export_points_format = "arrow.gz"
        def data = exportData([point(1, 2, 3, 0.5)], [vector(0.1)], ["f1"])

        PointsExporter.exportPoints(data, tempDir.toString(), "arrow_gz")

        def file = new File("$tempDir/arrow_gz_points.arrow.gz")
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }

    @Test
    void supportsCompressedArrowZstd() {
        Params.inst.export_points_format = "arrow.zst"
        def data = exportData([point(1, 2, 3, 0.5)], [vector(0.1)], ["f1"])

        PointsExporter.exportPoints(data, tempDir.toString(), "arrow_zst")

        def file = new File("$tempDir/arrow_zst_points.arrow.zst")
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }

    // --- Helpers ---

    private static PointExportData exportData(List<LabeledPoint> points, List<FeatureVector> vectors, List<String> header) {
        PointExportData.create(points, vectors, header)
    }

    private static LabeledPoint point(double x, double y, double z, double score) {
        def atom = new AtomImpl()
        atom.coords = [x, y, z] as double[]
        def lp = new LabeledPoint(atom)
        lp.score = score
        return lp
    }

    private static FeatureVector vector(double... values) {
        new DoubleVector(values)
    }

    private File outputFile(String label) {
        new File("$tempDir/${label}_points.csv")
    }
}
