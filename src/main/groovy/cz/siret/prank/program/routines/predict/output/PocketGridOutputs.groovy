package cz.siret.prank.program.routines.predict.output

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.predict.output.descriptors.PocketDescriptorRegistry
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid
import cz.siret.prank.program.routines.predict.output.grid.PocketGridBuilder
import cz.siret.prank.program.routines.predict.output.grid.PocketGridConfig
import cz.siret.prank.program.visualization.renderers.PocketGridChimeraXRenderer
import cz.siret.prank.program.visualization.renderers.PocketGridPymolRenderer
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Facade for the pocket-grid output pipeline. Routines call
 * {@link #exportIfEnabled} once per item; this class checks the gating
 * params, builds the grid once, and dispatches to each exporter/renderer.
 *
 * <p>Pulled out of the routines so they don't grow a wall of imports or
 * repeat the same orchestration in two places (predict + rescore).
 */
@Slf4j
@CompileStatic
final class PocketGridOutputs {

    private PocketGridOutputs() {}

    /**
     * Build the pocket grid (only if needed) and run the enabled exporters/renderer.
     * No-op when none of the three gates are set.
     *
     * <p>The grid build is the dominant per-protein cost (lattice generation,
     * range-query assignment, morph-closing fill). It is skipped when neither
     * {@code export_pocket_grid} nor {@code vis_pocket_grid} is set AND
     * every selected descriptor opts out via {@link
     * cz.siret.prank.program.routines.predict.output.descriptors.PocketDescriptor#needsGrid}.
     * Today the grid-free descriptors are {@code num_residues},
     * {@code num_surface_atoms}, {@code pocket_net_charge},
     * {@code pocket_charge_polarity}, and {@code pocket_dipole_magnitude};
     * selecting only those alongside {@code -export_pocket_grid 0} runs
     * almost-zero overhead per protein.
     */
    static void exportIfEnabled(Prediction prediction, Protein protein,
                                String outdir, String label) {
        Params p = Params.inst
        if (!p.export_pocket_grid
                && !p.export_pocket_descriptors
                && !p.vis_pocket_grid) {
            return
        }

        List<? extends Pocket> pockets = prediction.outputPockets

        // Grid is needed if it's exported, rendered, OR if any selected descriptor reads it.
        boolean needGrid =
                p.export_pocket_grid ||
                p.vis_pocket_grid ||
                (p.export_pocket_descriptors && anySelectedDescriptorNeedsGrid(p.pocket_descriptors))

        PocketGrid grid = needGrid ? PocketGridBuilder.build(protein, pockets, PocketGridConfig.fromParams(p)) : null

        // The facade is the single point that decides which outputs run. Each consumer
        // is invoked unconditionally below — the exporters used to re-check their flag,
        // which created duplicate "is this enabled?" logic. Validator enforces
        // vis_pocket_grid ⇒ export_pocket_grid, so grid is non-null whenever the
        // viz branch is taken.
        if (p.export_pocket_grid) {
            PocketGridExporter.export(grid, protein, pockets, outdir, label)
        }
        if (p.export_pocket_descriptors) {
            PocketDescriptorsExporter.export(pockets, grid, protein, outdir, label)
        }
        if (p.visualizations && p.vis_pocket_grid) {
            // The grid scripts overlay on top of whatever the main visualizer wrote —
            // PML on top of {label}_pymol.pml, CXC on top of {label}_chimerax.cxc.
            // We emit each grid script only when its parent renderer is in
            // vis_renderers, so we don't write a file that @-includes a missing one.
            //
            // Resolve the auto-scale sentinel for the volume-surface radius. -1 means
            // "scale with spacing" → 0.85 × pocket_grid_spacing (above the 3D-diagonal
            // merge threshold ~0.87 × spacing). Any positive user override stays as-is.
            double volRadius = p.vis_pocket_grid_volume_radius >= 0
                    ? p.vis_pocket_grid_volume_radius
                    : 0.85d * p.pocket_grid_spacing
            if ('pymol' in p.vis_renderers) {
                PocketGridPymolRenderer.render(grid, volRadius,
                        p.vis_pocket_grid_gaussian_iso, outdir, label)
            }
            if ('chimerax' in p.vis_renderers) {
                // ChimeraX renderer has only 2 layers (spheres + vdW surface); the PyMOL-only
                // gaussian-iso param is not passed. The combined sidecar is shared with the
                // PyMOL overlay — PocketGridPdbSidecar.ensureWritten makes the second
                // renderer's write a no-op when the first already wrote it.
                PocketGridChimeraXRenderer.render(grid, volRadius, outdir, label)
            }
        }
    }

    private static boolean anySelectedDescriptorNeedsGrid(List<String> selected) {
        // Null-safe: a missing/empty list means no descriptors → no grid needed.
        // (The validator rejects null/blank entries inside a non-null list, so
        // `name` here is always a registered descriptor name.)
        if (selected == null) return false
        for (String name : selected) {
            if (PocketDescriptorRegistry.get(name).needsGrid()) return true
        }
        return false
    }

}
