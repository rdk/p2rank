package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Exports SAS points with their feature vectors and optionally predicted scores.
 * Delegates to TableExporter for format-specific logic.
 *
 * Supported formats: csv, csv.gz, csv.zst, arrow, arrow.gz, arrow.zst, parquet
 */
@Slf4j
@CompileStatic
class PointsExporter {

    private PointsExporter() {}

    /**
     * Export points if enabled in params. Catches and logs errors.
     */
    static void tryExportPoints(PointExportData data, String outdir, String label) {
        if (!Params.inst.export_points || data == null) {
            return
        }
        try {
            exportPoints(data, outdir, label)
        } catch (Throwable e) {
            log.error("Failed to export points for {}: {}", label, e.message, e)
        }
    }

    /**
     * Export points to file. Uses format from Params.
     */
    static void exportPoints(PointExportData data, String outdir, String label) {
        exportPoints(data, outdir, label, Params.inst.export_points_format)
    }

    /**
     * Export points to file with explicit format.
     * Used by export-points command (always exports, format passed directly).
     */
    static void exportPoints(PointExportData data, String outdir, String label, String format) {
        String filepath = "${outdir}/${label}_points.${format}"

        long start = System.currentTimeMillis()
        TableExporter.export(data, filepath, format)
        long elapsed = System.currentTimeMillis() - start

        log.info("Exported {} points to {} ({}ms)", data.rowCount, filepath, elapsed)
    }

}
