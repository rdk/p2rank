package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Writes {@code {label}_pocket_grid.{format}}.
 *
 * <p>Gating is the caller's responsibility ({@link PocketGridOutputs} checks
 * {@code export_pocket_grid} before invoking {@link #export}). Errors during
 * write are caught and logged — a failed write must not abort the prediction
 * run for one protein.
 */
@Slf4j
@CompileStatic
final class PocketGridExporter {

    private PocketGridExporter() {}

    /**
     * Write the grid as configured. Caller has already verified
     * {@code export_pocket_grid} and that {@code grid} is non-null.
     * Exceptions are caught and logged so an output-stage failure on one
     * protein doesn't take down the rest of the dataset.
     */
    static void export(PocketGrid grid, Protein protein, List<? extends Pocket> pockets,
                       String outdir, String label) {
        try {
            doExport(grid, protein, pockets, outdir, label,
                    Params.inst.pocket_grid_format,
                    Params.inst.pocket_grid_include_unassigned,
                    Params.inst.pocket_grid_point_descriptors)
        } catch (Throwable e) {
            log.error("Failed to export pocket grid for {}: {}", label, e.message, e)
        }
    }

    private static void doExport(PocketGrid grid, Protein protein, List<? extends Pocket> pockets,
                                 String outdir, String label,
                                 String format, boolean includeUnassigned, List<String> descriptorNames) {
        PocketGridRows data = new PocketGridRows(grid, includeUnassigned, protein, pockets, descriptorNames)
        String filepath = "${outdir}/${label}_pocket_grid.${format}"

        long start = System.currentTimeMillis()
        TableExporter.export(data, filepath, format)
        long elapsed = System.currentTimeMillis() - start

        log.info("Exported {} pocket-grid rows to {} ({}ms)", data.rowCount, filepath, elapsed)
    }

}
