package cz.siret.prank.program.visualization.renderers

import cz.siret.prank.program.routines.predict.output.grid.PocketGrid
import cz.siret.prank.program.visualization.PredictionVisualizer
import cz.siret.prank.utils.ColorUtils
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.awt.Color
import java.util.Locale

/**
 * ChimeraX parallel of {@link PocketGridPymolRenderer}.
 *
 * <p>Emits {@code {label}_pocket_grid.cxc} alongside the standard
 * {@code {label}_chimerax.cxc}. The grid script opens the standard cxc first
 * (inheriting the protein + points scene) and then loads the same
 * {@code data/{label}_pocket_grid.pdb.gz} sidecar that the PyMOL overlay uses.
 *
 * <p>Two togglable layers, each parented in its own top-level model so the
 * ChimeraX Models panel shows a checkbox per layer (toggle the parent → every
 * pocket's contribution to that layer hides at once):
 * <ul>
 *   <li>{@code #99} — discrete-spheres parent. {@code split #99 residues}
 *       partitions the atoms into per-pocket child submodels
 *       ({@code #99.1}, {@code #99.2}, …). Toggle {@code #99} to hide/show
 *       all spheres; toggle a child to hide/show one pocket's spheres.</li>
 *   <li>{@code #100} — vdW-radius surface parent (its own atoms are hidden);
 *       child models {@code #100.1}, {@code #100.2}, … are the per-pocket
 *       surface meshes. Toggle {@code #100} to hide/show every surface;
 *       toggle a child to hide/show one pocket's surface.</li>
 * </ul>
 *
 * <p>The shared grid PDB sidecar is opened twice (once per layer) so the two
 * layers are independent in the model tree. Each open costs only the atom-load —
 * negligible for our point sets.
 *
 * <p>The PyMOL overlay also offers Gaussian-iso and convex-hull layers; both are
 * skipped here. ChimeraX cxc is pure commands (no inline Python), and
 * {@code volume gaussian} returns a model with an auto-assigned ID that cxc
 * can't reliably reference for follow-up styling — running it inline would
 * leave naked volumes hanging in the scene. Power users can build the gaussian
 * blob manually after opening the cxc:
 * <pre>volume gaussian #99 sDev 1.0 step 0.5</pre>
 *
 * <p>Model IDs: the standard cxc opens protein ({@code #1}) and points
 * ({@code #2}). This script opens the grid PDB as {@code #99} (spheres) and
 * {@code #100} (surfaces source) — stable IDs so the per-pocket selectors
 * don't depend on what the standard cxc opens.
 *
 * <p><b>Requires ChimeraX 1.11+ (Open Source build).</b> The {@code surface}
 * command is intrinsically SES (solvent-excluded surface) and doesn't accept
 * {@code probeRadius 0} — the SES geometry path produces empty arrays and
 * crashes with a numpy broadcast error in 1.8 through at least 1.12rc. We
 * emit a small non-zero probe instead ({@link #SURFACE_PROBE_RADIUS}); the
 * visible surface radius in ChimeraX is therefore
 * {@code vis_pocket_grid_volume_radius + SURFACE_PROBE_RADIUS}, slightly
 * larger than the value PyMOL renders (PyMOL's {@code surface} honors
 * {@code solvent_radius 0} for true vdW surfaces).
 */
@Slf4j
@CompileStatic
final class PocketGridChimeraXRenderer {

    /**
     * Discrete-spheres atom radius, expressed as a ratio of grid spacing.
     * Effective radius = {@code SPHERE_RADIUS_RATIO × spacing}.
     * 0.417 × 1.2 ≈ 0.5 Å at default spacing — matches the PyMOL overlay.
     */
    private static final double SPHERE_RADIUS_RATIO = 0.417d

    /**
     * Transparency (%) applied to the inherited protein surface so the grid
     * layers are visible through it. ChimeraX takes percentages (0-100); the
     * PyMOL renderer uses {@code PROTEIN_TRANSPARENCY = 0.5} for the same
     * effect (PyMOL units = 0..1) — the two values are not numerically
     * matched because the renderers compose differently with the underlying
     * protein cartoon.
     */
    private static final int PROTEIN_TRANSPARENCY_PCT = 70

    /**
     * Transparency (%) for the grid's vdW-radius surface — slightly translucent
     * so the inner sphere layer still shows. Matches the PyMOL renderer's
     * {@code 0.2} fraction (PyMOL units = 0..1; ChimeraX = 0..100).
     */
    private static final int VOLUME_SURFACE_TRANSPARENCY_PCT = 20

    /**
     * Explicit ChimeraX model ID for the discrete-spheres layer. Spheres are
     * rendered from this model's atoms directly; toggling this model in the
     * Models panel hides every sphere at once.
     */
    private static final int SPHERES_MODEL_ID = 99

    /**
     * Explicit ChimeraX model ID for the vdW-surface layer. Atoms of this model
     * are hidden — it exists only to parent the per-pocket surface child models
     * ({@code #100.1}, {@code #100.2}, …) so toggling this parent in the Models
     * panel hides every pocket's surface at once. Without a separate parent,
     * the surface children would live under {@code #SPHERES_MODEL_ID} and the
     * tree checkbox would conflate the two layers.
     */
    private static final int SURFACES_MODEL_ID = 100

    /**
     * Solvent probe radius for ChimeraX's {@code surface} command, expressed as a
     * ratio of grid spacing. Effective probe = {@code SURFACE_PROBE_RADIUS_RATIO × spacing}.
     *
     * <p>Intentionally non-zero — {@code probeRadius 0} crashes ChimeraX's SES
     * geometry path with a numpy broadcast error ({@code shapes (0,3) (0,)}) in every
     * version tested (1.8 through 1.12rc). SES is fundamentally defined with a non-zero
     * probe, so this is unlikely to be "fixable" upstream.
     *
     * <p>0.333 × 1.2 ≈ 0.4 Å at default spacing. The visible surface sits at
     * {@code vis_volume_radius + probe}, slightly bulgier than the same surface
     * in PyMOL (which honors solvent_radius 0 cleanly).
     */
    private static final double SURFACE_PROBE_RADIUS_RATIO = 0.333d

    /**
     * Surface mesh grid spacing, expressed as a ratio of grid spacing.
     * Effective mesh resolution = {@code SURFACE_GRID_SPACING_RATIO × spacing}.
     *
     * <p>0.25 × 1.2 = 0.3 Å at default — noticeably smoother than ChimeraX's 0.5
     * default. Scaling with spacing keeps mesh fineness proportional: at a coarser
     * lattice the mesh can be coarser too without visible faceting.
     */
    private static final double SURFACE_GRID_SPACING_RATIO = 0.25d

    private PocketGridChimeraXRenderer() {}

    /**
     * @param grid          pocket grid to render
     * @param volumeRadius  per-grid-point vdW radius (Å) for the surface layer
     *                      ({@code vis_pocket_grid_volume_radius})
     * @param outdir        root output directory (parent of {@code visualizations/})
     * @param label         per-protein label (typically {@code item.label})
     */
    static void render(PocketGrid grid, double volumeRadius, String outdir, String label) {
        if (grid == null) return
        int maxRank = 0
        for (Integer rank : grid.pocketToPointIndices.keySet()) {
            if (rank > maxRank) maxRank = rank
        }
        if (maxRank == 0) {
            log.info('PocketGridChimeraXRenderer: no pockets to render for [{}]', label)
            return
        }

        String vizDir = Futils.mkdirs("${outdir}/visualizations")
        String dataDir = Futils.mkdirs("${vizDir}/data")

        String pdbPath = "${dataDir}/${label}_pocket_grid.pdb.gz"
        String cxcPath = "${vizDir}/${label}_pocket_grid.cxc"

        // Idempotent write — the combined PDB is shared with the PyMOL overlay,
        // so whichever renderer runs first writes it and the second is a no-op.
        PocketGridPdbSidecar.ensureWritten(grid, pdbPath)
        // Per-pocket sidecars are ChimeraX-only — used to seed per-pocket sphere
        // submodels via `open … id 99.N`, since ChimeraX `split` can't partition
        // by residue.
        LinkedHashMap<Integer, String> perPocketBasenames = PocketGridPdbSidecar.writePerPocket(grid, dataDir, label)
        writeCxc(volumeRadius, grid.spacing, label, perPocketBasenames, cxcPath)

        log.info('PocketGridChimeraXRenderer: wrote {} (+ {} per-pocket sidecars) and {}',
                pdbPath, perPocketBasenames.size(), cxcPath)
    }

    private static void writeCxc(double volumeRadius, double spacing, String label,
                                 LinkedHashMap<Integer, String> perPocketBasenames, String cxcPath) {
        int maxRank = 0
        for (Integer rank : perPocketBasenames.keySet()) {
            if (rank > maxRank) maxRank = rank
        }
        List<Color> palette = PredictionVisualizer.generatePocketColors(maxRank)

        StringBuilder cxc = new StringBuilder()
        cxc.append("# Pocket grid visualization for ${label} (ChimeraX)\n")
        double probeRadius = SURFACE_PROBE_RADIUS_RATIO * spacing
        double surfaceGrid = SURFACE_GRID_SPACING_RATIO * spacing
        cxc.append("# Tested with ChimeraX 1.11+. The vdW surface uses a small non-zero probe\n")
        cxc.append("# (${String.format(Locale.ROOT, '%.3f', probeRadius)} Å = ${SURFACE_PROBE_RADIUS_RATIO} × spacing) because ChimeraX SES crashes on\n")
        cxc.append("# probeRadius 0 (numpy bug, persists through 1.12rc). Visible surface radius\n")
        cxc.append("# = volumeRadius + probe.\n")
        cxc.append("#\n")
        cxc.append("# Layered on top of ${label}_chimerax.cxc — open this in ChimeraX to inspect\n")
        cxc.append("# predicted pocket regions alongside the full protein/ligand/cofactor scene.\n")
        cxc.append("#\n")
        cxc.append("# Two layers — each lives under its OWN top-level model, so the Models panel\n")
        cxc.append("# shows a checkbox per layer (toggle the parent → every pocket hides at once):\n")
        cxc.append("#\n")
        cxc.append("#   #${SPHERES_MODEL_ID}                — spheres parent  (atoms shown as spheres, split per pocket)\n")
        cxc.append("#   #${SPHERES_MODEL_ID}.1, #${SPHERES_MODEL_ID}.2, …  — per-pocket sphere submodels, children of #${SPHERES_MODEL_ID}\n")
        cxc.append("#   #${SURFACES_MODEL_ID}               — surfaces parent (atoms hidden; exists only to group the children)\n")
        cxc.append("#   #${SURFACES_MODEL_ID}.1, #${SURFACES_MODEL_ID}.2, …  — per-pocket vdW surfaces, children of #${SURFACES_MODEL_ID}\n")
        cxc.append("#\n")
        cxc.append("# Command-line equivalents:\n")
        cxc.append("#   hide #${SPHERES_MODEL_ID}    show #${SPHERES_MODEL_ID}    — toggle all spheres\n")
        cxc.append("#   hide #${SURFACES_MODEL_ID}    show #${SURFACES_MODEL_ID}    — toggle all surfaces\n")
        cxc.append("#   hide #${SPHERES_MODEL_ID}.2  show #${SURFACES_MODEL_ID}.1 — toggle one pocket's spheres / one pocket's surface\n")
        cxc.append("#\n")
        cxc.append("# The PyMOL overlay also offers Gaussian-iso and convex-hull layers — both omitted\n")
        cxc.append("# here. ChimeraX cxc is command-only (no inline Python), and `volume gaussian`\n")
        cxc.append("# returns an auto-IDed model the script can't reliably style afterward. If you\n")
        cxc.append("# want the gaussian blob, run it manually after opening this cxc:\n")
        cxc.append("#   volume gaussian #${SPHERES_MODEL_ID} sDev 1.0 step 0.5\n\n")

        // Inherit the standard scene
        cxc.append("open ${label}_chimerax.cxc\n\n")

        // Make protein surface semi-transparent so grid layers behind are visible.
        cxc.append("transparency #1 ${PROTEIN_TRANSPARENCY_PCT}\n\n")

        // Named colors used by both layers — same palette as the standard pml.
        // Iterate the present ranks only (the layer loops below do the same),
        // so the .cxc doesn't emit unreferenced `color name pgc_N` lines for
        // pockets that have no grid points.
        for (Integer rank : perPocketBasenames.keySet()) {
            String hex = ColorUtils.colorToHex(palette.get(rank - 1))
            cxc.append("color name pgc_${rank} ${hex}\n")
        }

        // ---- Layer 1: discrete spheres — parent model #SPHERES_MODEL_ID, one child per pocket ----
        // Each pocket is loaded from its own per-pocket PDB sidecar as a submodel
        // (#SPHERES_MODEL_ID.N). ChimeraX's `split` command can't partition by residue
        // for HETATM-only structures, so we partition on disk instead. The tree shows
        // #SPHERES_MODEL_ID as a parent group with children #SPHERES_MODEL_ID.1,
        // #SPHERES_MODEL_ID.2, … — symmetric with the surfaces layer.
        // `rename` calls give each model a human-readable name in the Models panel.
        cxc.append("\n")
        for (Map.Entry<Integer, String> entry : perPocketBasenames.entrySet()) {
            int rank = entry.key
            String basename = entry.value
            cxc.append("open data/${basename} format pdb id ${SPHERES_MODEL_ID}.${rank}\n")
            cxc.append("rename #${SPHERES_MODEL_ID}.${rank} \"pocket ${rank}\"\n")
            cxc.append("color #${SPHERES_MODEL_ID}.${rank} pgc_${rank}\n")
        }
        cxc.append("rename #${SPHERES_MODEL_ID} \"pocket grid spheres\"\n")
        cxc.append("style #${SPHERES_MODEL_ID} sphere\n")
        double sphereRadius = SPHERE_RADIUS_RATIO * spacing
        cxc.append("size #${SPHERES_MODEL_ID} atomRadius ${String.format(Locale.ROOT, '%.3f', sphereRadius)}\n")

        // ---- Layer 2: vdW-radius surface — parent model #SURFACES_MODEL_ID, one child per pocket ----
        // Loading the grid PDB a second time gives the surface layer its own parent model,
        // so toggling #SURFACES_MODEL_ID in the Models panel hides every pocket surface at
        // once (independent of #SPHERES_MODEL_ID). The atoms of #SURFACES_MODEL_ID are
        // hidden — they exist only to seed the surface meshes and to inherit the per-pocket
        // colour into the children (#SURFACES_MODEL_ID.1, #SURFACES_MODEL_ID.2, …).
        cxc.append("\nopen data/${label}_pocket_grid.pdb.gz format pdb id ${SURFACES_MODEL_ID}\n")
        cxc.append("rename #${SURFACES_MODEL_ID} \"pocket grid surfaces\"\n")
        cxc.append("hide #${SURFACES_MODEL_ID} atoms\n")
        for (Integer rank : perPocketBasenames.keySet()) {
            cxc.append("color #${SURFACES_MODEL_ID}:${rank} pgc_${rank}\n")
        }
        cxc.append("setattr #${SURFACES_MODEL_ID} atoms radius ${String.format(Locale.ROOT, '%.3f', volumeRadius)}\n")
        String probeStr = String.format(Locale.ROOT, '%.3f', probeRadius)
        String gridStr = String.format(Locale.ROOT, '%.3f', surfaceGrid)
        // surface #N:R creates a submodel with the next-free sub-ID. Iterate only ranks
        // that actually have atoms in the loaded PDB (perPocketBasenames.keySet — empty
        // pockets are dropped by the sidecar writer); subId tracks the IDs ChimeraX
        // actually assigns. A rank-skip (rare, but possible if upstream skips a rank)
        // used to mis-target the rename — e.g. rank 1 + rank 3 would have produced
        // sub-IDs 1, 2 but the rename loop tried to rename .1, .3.
        int subId = 1
        for (Integer rank : perPocketBasenames.keySet()) {
            cxc.append("surface #${SURFACES_MODEL_ID}:${rank} probeRadius ${probeStr} gridSpacing ${gridStr}\n")
            cxc.append("rename #${SURFACES_MODEL_ID}.${subId} \"pocket ${rank}\"\n")
            subId++
        }
        cxc.append("transparency #${SURFACES_MODEL_ID} ${VOLUME_SURFACE_TRANSPARENCY_PCT} surfaces\n")

        cxc.append("\nview\n")

        Futils.writeFile(cxcPath, cxc.toString())
    }

}
