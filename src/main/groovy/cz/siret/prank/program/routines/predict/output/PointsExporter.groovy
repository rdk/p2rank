package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.io.BufferedWriter
import java.io.FileWriter
import java.io.OutputStreamWriter
import java.io.PrintWriter

import static cz.siret.prank.utils.Formatter.format

/**
 * Exports SAS points with their feature vectors and predicted scores to CSV.
 */
@Slf4j
@CompileStatic
class PointsExporter {

    private static final Set<String> VALID_FORMATS = ["csv", "csv.gz", "csv.zst"] as Set
    private static final int BUFFER_SIZE = 65536

    private PointsExporter() {
        // static utility class
    }

    /**
     * Safely export points if export is enabled.
     * Handles null checks and exceptions internally.
     *
     * @param exportData Export data containing points, vectors, and header (may be null)
     * @param outdir Output directory
     * @param label Protein/file label for the output filename
     */
    static void tryExportPoints(PointExportData exportData, String outdir, String label) {
        if (!Params.inst.export_points || exportData == null) {
            return
        }
        try {
            exportPoints(exportData, outdir, label)
        } catch (Exception e) {
            log.error "Failed to export points for {}: {}", label, e.message
        }
    }

    /**
     * Export points to a CSV file with the specified format.
     *
     * @param exportData Export data containing points, vectors, and header
     * @param outdir Output directory
     * @param label Protein/file label for the output filename
     */
    static void exportPoints(PointExportData exportData, String outdir, String label) {

        List<LabeledPoint> labeledPoints = exportData.labeledPoints
        List<FeatureVector> featureVectors = exportData.featureVectors
        List<String> featureHeader = exportData.featureHeader

        // Input validation
        if (labeledPoints == null || featureVectors == null || featureHeader == null) {
            log.error "Cannot export points: null input (labeledPoints={}, featureVectors={}, featureHeader={})",
                labeledPoints != null, featureVectors != null, featureHeader != null
            return
        }

        if (labeledPoints.size() != featureVectors.size()) {
            log.error "Cannot export points: size mismatch between labeledPoints ({}) and featureVectors ({})",
                labeledPoints.size(), featureVectors.size()
            return
        }

        String format = Params.inst.export_points_format

        // Format validation
        if (!VALID_FORMATS.contains(format)) {
            log.warn "Unknown export_points_format '{}', falling back to 'csv'. Valid formats: {}", format, VALID_FORMATS
            format = "csv"
        }

        String fname = "$outdir/${label}_points.${format}"

        log.info "Exporting {} points to {}", labeledPoints.size(), fname

        PrintWriter writer = createWriter(fname, format)
        try {
            // Write header
            writer.print("x,y,z,score")
            for (String feat : featureHeader) {
                writer.print(",")
                writer.print(feat)
            }
            writer.println()

            // Write data rows
            for (int i = 0; i < labeledPoints.size(); i++) {
                LabeledPoint lp = labeledPoints.get(i)
                double[] coords = lp.getCoords()
                double[] features = featureVectors.get(i).getArray()

                writer.print(fmt(coords[0]))
                writer.print(",")
                writer.print(fmt(coords[1]))
                writer.print(",")
                writer.print(fmt(coords[2]))
                writer.print(",")
                writer.print(fmt(lp.score))

                for (double f : features) {
                    writer.print(",")
                    writer.print(fmt(f))
                }
                writer.println()
            }
        } finally {
            writer.close()
        }

        log.info "Points export completed: {}", fname
    }

    private static String fmt(double d) {
        return format(d, 7)
    }

    private static PrintWriter createWriter(String fname, String format) {
        if (format == "csv.gz") {
            return new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(Futils.getGzipOutputStream(fname)), BUFFER_SIZE))
        } else if (format == "csv.zst") {
            return new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(Futils.getZstdOutputStream(fname)), BUFFER_SIZE))
        } else {
            return new PrintWriter(new BufferedWriter(new FileWriter(fname), BUFFER_SIZE))
        }
    }
}
