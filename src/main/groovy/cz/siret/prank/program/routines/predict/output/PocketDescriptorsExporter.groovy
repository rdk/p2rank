package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Writes {@code {label}_pocket_descriptors.{format}}.
 *
 * <p>Gating is the caller's responsibility ({@link PocketGridOutputs} checks
 * {@code export_pocket_descriptors} before invoking {@link #export}).
 * {@code grid} is permitted to be null — happens when every selected
 * descriptor opts out of {@code needsGrid()}, in which case the grid build
 * was correctly skipped.
 */
@Slf4j
@CompileStatic
final class PocketDescriptorsExporter {

    private PocketDescriptorsExporter() {}

    /**
     * Write the descriptors file. Caller has already verified
     * {@code export_pocket_descriptors}. {@code grid} may be null when only
     * grid-free descriptors are selected.
     */
    static void export(List<? extends Pocket> pockets, PocketGrid grid,
                       Protein protein, String outdir, String label) {
        try {
            doExport(pockets, grid, protein, outdir, label,
                    Params.inst.pocket_grid_format,
                    Params.inst.pocket_descriptors)
        } catch (Throwable e) {
            log.error("Failed to export pocket descriptors for {}: {}", label, e.message, e)
        }
    }

    private static void doExport(List<? extends Pocket> pockets, PocketGrid grid, Protein protein,
                                 String outdir, String label, String format, List<String> descriptorNames) {
        PocketDescriptorsRows data = new PocketDescriptorsRows(
                pockets, descriptorNames, protein, grid)
        String filepath = "${outdir}/${label}_pocket_descriptors.${format}"

        long start = System.currentTimeMillis()
        TableExporter.export(data, filepath, format)
        long elapsed = System.currentTimeMillis() - start

        log.info("Exported {} pocket descriptors to {} ({}ms)", data.rowCount, filepath, elapsed)
    }

}
