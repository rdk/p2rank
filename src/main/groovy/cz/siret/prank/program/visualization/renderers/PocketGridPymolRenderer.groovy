package cz.siret.prank.program.visualization.renderers

import cz.siret.prank.program.routines.predict.output.grid.PocketGrid
import cz.siret.prank.program.visualization.PredictionVisualizer
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.awt.Color
import java.util.Locale

/**
 * Renders the per-pocket grid as a PyMOL overlay on top of the standard
 * pocket visualization.
 *
 * <p>Lives under {@code visualization/renderers/} alongside the main
 * {@link PymolRenderer} for consistency with the project's renderer package
 * convention. The corresponding data classes — {@code PocketGrid},
 * {@code PocketGridBuilder}, {@code PocketGridConfig} — live in
 * {@code program/routines/predict/output/grid/}.
 *
 * <p>Writes two files:
 * <ul>
 *   <li>{@code {outdir}/visualizations/data/{label}_pocket_grid.pdb.gz} — one HETATM
 *       per {@code (point, pocket)} pair. Pocket rank lives in the residue-sequence
 *       column so PyMOL can split objects by {@code resi N}.</li>
 *   <li>{@code {outdir}/visualizations/{label}_pocket_grid.pml} — a thin overlay
 *       that {@code @}-includes the main pocket PML ({@code {label}_pymol.pml})
 *       and adds <b>four togglable layers per pocket</b>:
 *       <ul>
 *         <li>{@code pocket_grid_N}  — discrete points as small spheres (ON by default)</li>
 *         <li>{@code pocket_vol_N}   — vdW-radius surface union (volumetric blob, ON by default)</li>
 *         <li>{@code pocket_gauss_N} — Gaussian-density iso-surface (smooth blob, OFF)</li>
 *         <li>{@code pocket_hull_N}  — convex-hull wireframe (OFF, needs scipy)</li>
 *       </ul>
 *       Each layer is an independent PyMOL object — toggle via the right-panel
 *       eye icon or {@code enable}/{@code disable pocket_<layer>_*}.</li>
 * </ul>
 *
 * <p>Pocket ranks are capped at 9999 by the PDB residue-sequence column width
 * (4 chars). Real protein pockets stay well under 100; the limit is documented
 * for completeness.
 *
 * <p>This renderer has its own gate ({@code -vis_pocket_grid}) and is emitted
 * only when {@code pymol} is in {@code vis_renderers} — it is a power-user
 * output that shouldn't be implicit. It respects the global
 * {@code -visualizations} switch and the standard pml (which the overlay
 * {@code @}-includes) is only present when {@code pymol} is in
 * {@code vis_renderers}; the matching gate in {@link
 * cz.siret.prank.program.routines.predict.output.PocketGridOutputs} keeps
 * the two in sync.
 */
@Slf4j
@CompileStatic
final class PocketGridPymolRenderer {

    /**
     * Discrete-spheres visible radius, expressed as a ratio of grid spacing.
     * Effective sphere radius = {@code SPHERE_RADIUS_RATIO × spacing}.
     * 0.425 at default spacing 1.2 gives ~0.51 Å — same look as the old fixed
     * {@code sphere_scale = 0.3} on default C atoms (vdW 1.7, 0.3 × 1.7 = 0.51).
     */
    private static final double SPHERE_RADIUS_RATIO = 0.425d

    /** PyMOL's default vdW for carbon — used to back out sphere_scale from a desired visible radius. */
    private static final double DEFAULT_C_VDW = 1.7d

    /** Default transparency for the volumetric surface — slightly transparent so the discrete spheres inside still read. */
    private static final double VOLUME_TRANSPARENCY = 0.2d

    /**
     * Transparency applied to the protein surface in the grid overlay so the
     * volumetric pocket layer is visible through it. Affects only this pml — the
     * standalone {@code {label}_pymol.pml} script keeps the protein opaque.
     */
    private static final double PROTEIN_TRANSPARENCY = 0.5d

    /**
     * Map resolution (Å) for the Gaussian density layer. Smaller = smoother
     * iso-surface, slower compute. 0.5 Å is a reasonable compromise — fine
     * enough to capture detail at typical {@code pocket_grid_spacing}.
     */
    private static final double GAUSSIAN_MAP_GRID = 0.5d

    /**
     * Minimum B-factor used by PyMOL's gaussian map (via {@code gaussian_b_floor}).
     * Our HETATM points are written with B=0; without a floor, the map would be
     * degenerate. 30 is a typical mid-protein B-factor and gives well-defined peaks.
     */
    private static final int GAUSSIAN_B_FLOOR = 30

    private PocketGridPymolRenderer() {}

    /**
     * @param grid          pocket grid to render
     * @param volumeRadius  per-grid-point sphere radius (Å) for the
     *                      {@code pocket_vol_N} layer ({@code vis_pocket_grid_volume_radius}).
     * @param gaussianIso   iso-surface threshold for the {@code pocket_gauss_N}
     *                      gaussian-density layer ({@code vis_pocket_grid_gaussian_iso}).
     * @param outdir        root output directory (parent of {@code visualizations/})
     * @param label         per-protein label (typically {@code item.label})
     */
    static void render(PocketGrid grid, double volumeRadius, double gaussianIso,
                       String outdir, String label) {
        if (grid == null) return
        int maxRank = 0
        for (Integer rank : grid.pocketToPointIndices.keySet()) {
            if (rank > maxRank) maxRank = rank
        }
        if (maxRank == 0) {
            log.info('PocketGridPymolRenderer: no pockets to render for [{}]', label)
            return
        }

        String vizDir = Futils.mkdirs("${outdir}/visualizations")
        String dataDir = Futils.mkdirs("${vizDir}/data")

        String pdbPath = "${dataDir}/${label}_pocket_grid.pdb.gz"
        String pmlPath = "${vizDir}/${label}_pocket_grid.pml"

        // Idempotent write — the combined PDB is shared with the ChimeraX overlay,
        // so whichever renderer runs first writes it and the second is a no-op.
        PocketGridPdbSidecar.ensureWritten(grid, pdbPath)
        writePml(maxRank, volumeRadius, gaussianIso, grid.spacing, label, pmlPath)

        log.info('PocketGridPymolRenderer: wrote {} and {}', pdbPath, pmlPath)
    }

    private static void writePml(int maxRank, double volumeRadius, double gaussianIso,
                                 double spacing, String label, String pmlPath) {
        // Palette must match the main PML's per-pocket palette: both use
        // PredictionVisualizer.generatePocketColors(N). They line up in practice (every
        // assigned pocket has rank in [1, N]); a rank-skip would silently shift color
        // indexing by one.
        List<Color> palette = PredictionVisualizer.generatePocketColors(maxRank)

        StringBuilder pml = new StringBuilder()
        pml.append("# Pocket grid visualization for ${label}\n")
        pml.append("# Layered on top of the standard pocket PML — run this in PyMOL to inspect\n")
        pml.append("# predicted pocket regions alongside the full protein/ligand/cofactor scene.\n")
        pml.append("#\n")
        pml.append("# Four independent layers per pocket — toggle each in the right-panel object\n")
        pml.append("# tree (click the eye icon) or via PyMOL commands:\n")
        pml.append("#   pocket_grid_N  — discrete grid points as spheres            (default: ON)\n")
        pml.append("#   pocket_vol_N   — vdW-radius surface union (translucent)     (default: ON)\n")
        pml.append("#   pocket_gauss_N — Gaussian-density iso-surface (smooth blob) (default: OFF)\n")
        pml.append("#   pocket_hull_N  — convex-hull wireframe (needs scipy)        (default: OFF)\n")
        pml.append("#\n")
        pml.append("# Each layer also has an all-pockets group for one-click toggle:\n")
        pml.append("#   pocket_grid_all   pocket_vol_all   pocket_gauss_all   pocket_hull_all\n")
        pml.append("# (Or use wildcards: `enable pocket_gauss_*`, `disable pocket_vol_*`, ...)\n\n")

        // Inherit the entire standard visualization (protein surface, ligands, cofactors,
        // SAS points, pocket centroids, palette). Single source of truth — when
        // PymolRenderer evolves, the grid view evolves with it.
        pml.append("@${label}_pymol.pml\n\n")

        // Make protein semi-transparent and show the cartoon ribbon underneath, so the
        // volumetric grid layers behind are visible AND the protein still reads as a
        // proper structure (matches the default ChimeraX feel). Local to this overlay —
        // the standalone _pymol.pml keeps protein opaque and surface-only.
        pml.append("set transparency, ${PROTEIN_TRANSPARENCY}, protein\n")
        pml.append("show cartoon, protein\n\n")

        pml.append("load data/${label}_pocket_grid.pdb.gz, pocket_grid_src\n")
        pml.append("hide everything, pocket_grid_src\n\n")

        // Per-pocket palette + per-pocket grid + vol objects (created from pocket_grid_src).
        for (int rank = 1; rank <= maxRank; rank++) {
            String colorName = "pgc_${rank}"
            pml.append("set_color ${colorName} = ${PymolRenderer.pyColor(palette.get(rank - 1))}\n")
            pml.append("create pocket_grid_${rank}, pocket_grid_src and resi ${rank}\n")
            pml.append("color ${colorName}, pocket_grid_${rank}\n")
            pml.append("create pocket_vol_${rank}, pocket_grid_src and resi ${rank}\n")
            pml.append("color ${colorName}, pocket_vol_${rank}\n")
            pml.append("set surface_color, ${colorName}, pocket_vol_${rank}\n")
        }

        // ---- Layer 1: discrete spheres (ON) ----
        // sphere_scale is computed from spacing so the visible sphere radius
        // (sphere_scale × default-C-vdW 1.7) scales with the lattice.
        double sphereScale = (SPHERE_RADIUS_RATIO * spacing) / DEFAULT_C_VDW
        pml.append("\nshow spheres, pocket_grid_*\n")
        pml.append("set sphere_scale, ${String.format(Locale.ROOT, '%.3f', sphereScale)}, pocket_grid_*\n")
        pml.append("group pocket_grid_all, pocket_grid_*\n")

        // ---- Layer 2: vdW-radius surface union (ON, translucent) ----
        // alter vdw + solvent_radius=0 → surface tracks volumeRadius exactly (no probe).
        // Grouped as pocket_vol_all so the whole layer can be toggled by one click in
        // the right-panel tree (or `disable pocket_vol_all` from the command line).
        pml.append("\nalter pocket_vol_*, vdw=${String.format(Locale.ROOT, '%.3f', volumeRadius)}\n")
        pml.append("set solvent_radius, 0, pocket_vol_*\n")
        pml.append("rebuild pocket_vol_*\n")
        pml.append("show surface, pocket_vol_*\n")
        pml.append("set transparency, ${VOLUME_TRANSPARENCY}, pocket_vol_*\n")
        pml.append("group pocket_vol_all, pocket_vol_*\n")

        // ---- Layer 3: Gaussian-density iso-surface (OFF) ----
        // map_new gaussian builds a 3D density field from atom positions; isosurface
        // extracts a mesh at the configured threshold. gaussian_b_floor forces a non-
        // trivial peak per atom (our HETATM B-factor is 0).
        pml.append("\nset gaussian_b_floor, ${GAUSSIAN_B_FLOOR}\n")
        for (int rank = 1; rank <= maxRank; rank++) {
            String mapName = "__pocket_gauss_map_${rank}"
            pml.append("map_new ${mapName}, gaussian, ${String.format(Locale.ROOT, '%.2f', GAUSSIAN_MAP_GRID)}, pocket_grid_src and resi ${rank}, 3.0\n")
            pml.append("isosurface pocket_gauss_${rank}, ${mapName}, ${String.format(Locale.ROOT, '%.2f', gaussianIso)}\n")
            pml.append("color pgc_${rank}, pocket_gauss_${rank}\n")
            pml.append("disable ${mapName}\n")
            pml.append("disable pocket_gauss_${rank}\n")
        }
        pml.append("group pocket_gauss_all, pocket_gauss_*\n")

        // ---- Layer 4: Convex-hull wireframe (OFF, needs scipy) ----
        // Computed in PyMOL's embedded Python: per pocket, get the grid points' coords,
        // build a 3D convex hull, emit triangle-edge CGO lines. Graceful no-op if scipy
        // is unavailable (Open Source PyMOL builds without scipy will just skip).
        pml.append("\npython\n")
        pml.append("from pymol.cgo import BEGIN, END, LINES, COLOR, VERTEX\n")
        pml.append("try:\n")
        pml.append("    from scipy.spatial import ConvexHull\n")
        pml.append("    _pocket_hull_ok = True\n")
        pml.append("except ImportError:\n")
        pml.append("    _pocket_hull_ok = False\n")
        pml.append("    print(\"pocket_hull layer: scipy not available — skipping convex-hull layer\")\n")
        pml.append("\n")
        pml.append("def _pocket_hull(sel, name, r, g, bcol):\n")
        pml.append("    if not _pocket_hull_ok:\n")
        pml.append("        return\n")
        pml.append("    coords = cmd.get_coords(sel)\n")
        pml.append("    if coords is None or len(coords) < 4:\n")
        pml.append("        return\n")
        pml.append("    hull = ConvexHull(coords)\n")
        pml.append("    cgo = [BEGIN, LINES, COLOR, r, g, bcol]\n")
        pml.append("    for tri in hull.simplices:\n")
        pml.append("        for k in range(3):\n")
        pml.append("            a, b = tri[k], tri[(k + 1) % 3]\n")
        pml.append("            cgo += [VERTEX, float(coords[a][0]), float(coords[a][1]), float(coords[a][2])]\n")
        pml.append("            cgo += [VERTEX, float(coords[b][0]), float(coords[b][1]), float(coords[b][2])]\n")
        pml.append("    cgo.append(END)\n")
        pml.append("    cmd.load_cgo(cgo, name)\n")
        pml.append("    cmd.disable(name)\n")
        pml.append("python end\n\n")
        pml.append("python\n")
        for (int rank = 1; rank <= maxRank; rank++) {
            Color c = palette.get(rank - 1)
            String r = String.format(Locale.ROOT, '%.3f', c.red / 255d)
            String g = String.format(Locale.ROOT, '%.3f', c.green / 255d)
            String b = String.format(Locale.ROOT, '%.3f', c.blue / 255d)
            pml.append("_pocket_hull(\"pocket_grid_src and resi ${rank}\", \"pocket_hull_${rank}\", ${r}, ${g}, ${b})\n")
        }
        pml.append("python end\n")
        pml.append("group pocket_hull_all, pocket_hull_*\n")

        pml.append("\ndelete pocket_grid_src\n")

        // Re-frame the camera once everything is loaded. The standard pml's own `orient`
        // ran inside the @-include before the grid was loaded.
        pml.append("\norient\n")

        Futils.writeFile(pmlPath, pml.toString())
    }

}
