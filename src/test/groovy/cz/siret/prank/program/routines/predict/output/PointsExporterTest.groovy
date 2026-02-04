package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.collectors.DoubleVector
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.AtomImpl
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path
import java.util.zip.GZIPInputStream

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class PointsExporterTest {

    @TempDir
    Path tempDir

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
        assertEquals("x,y,z,score,feat1,feat2", lines[0])
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

        assertEquals(6, values.length)
        assertTrue(values[0].contains("1.5"))
        assertTrue(values[1].contains("2.5"))
        assertTrue(values[2].contains("3.5"))
        assertTrue(values[3].contains("0.75"))
        assertTrue(values[4].contains("0.123"))
        assertTrue(values[5].contains("0.456"))
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
        assertTrue(content.startsWith("x,y,z,score,f1"))
    }

    @Test
    void supportsZstdCompression() {
        Params.inst.export_points_format = "csv.zst"
        def data = exportData([point(1, 2, 3, 0.5)], [vector(0.1)], ["f1"])

        PointsExporter.exportPoints(data, tempDir.toString(), "zstd")

        def zstFile = new File("$tempDir/zstd_points.csv.zst")
        assertTrue(zstFile.exists())

        String content = Futils.inputStream(zstFile.path).text
        assertTrue(content.startsWith("x,y,z,score,f1"))
    }

    @Test
    void fallsBackToCsvForUnknownFormat() {
        Params.inst.export_points_format = "parquet"
        def data = exportData([point(1, 2, 3, 0.5)], [vector(0.1)], ["f1"])

        PointsExporter.exportPoints(data, tempDir.toString(), "fallback")

        assertTrue(new File("$tempDir/fallback_points.csv").exists())
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
