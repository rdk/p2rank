package cz.siret.prank.program.visualization.renderers

import com.carrotsearch.hppc.LongIntHashMap
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Point
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path
import java.util.zip.GZIPInputStream

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class PocketGridPymolRendererTest {

    /** Sample volume radius used by most tests — well above the merge threshold for spacing=1.0. */
    private static final double VOL_RADIUS = 1.5d

    /** Sample gaussian iso-level used by most tests — sensible default. */
    private static final double GAUSSIAN_ISO = 0.5d

    @TempDir
    Path tempDir

    private static PocketGrid tinyGrid() {
        return RendererTestFixtures.tinyGrid()
    }

    @Test
    void writesPdbAndPmlAtExpectedPaths() {
        PocketGridPymolRenderer.render(tinyGrid(), VOL_RADIUS, GAUSSIAN_ISO, tempDir.toString(), "test")

        File pdb = new File("${tempDir}/visualizations/data/test_pocket_grid.pdb.gz")
        File pml = new File("${tempDir}/visualizations/test_pocket_grid.pml")
        assertTrue(pdb.exists(), "PDB sidecar not written")
        assertTrue(pml.exists(), "PML script not written")
        assertTrue(pdb.length() > 0)
        assertTrue(pml.length() > 0)
    }

    @Test
    void pmlIncludesMainPymolPmlAndAddsGridSpheres() {
        // The grid PML is a thin overlay — it @-includes the standard pml so the
        // protein/ligand/cofactor/SAS scene is inherited, then adds the per-pocket
        // sphere objects on top.
        PocketGridPymolRenderer.render(tinyGrid(), VOL_RADIUS, GAUSSIAN_ISO, tempDir.toString(), "test")
        String pml = new File("${tempDir}/visualizations/test_pocket_grid.pml").text

        assertTrue(pml.contains("@test_pymol.pml"),
                "grid PML must @-include the standard pml: ${pml}")

        assertTrue(pml.contains("create pocket_grid_1, pocket_grid_src and resi 1"))
        assertTrue(pml.contains("create pocket_grid_2, pocket_grid_src and resi 2"))
        assertTrue(pml.contains("set_color pgc_1 ="))
        assertTrue(pml.contains("set_color pgc_2 ="))
        assertTrue(pml.contains("show spheres, pocket_grid_*"))
        assertTrue(pml.contains("set sphere_scale"))
    }

    @Test
    void pmlEmitsVolumetricSurfaceLayer() {
        // Each pocket gets a pocket_vol_N object too — the volumetric surface layer.
        // Uses the configured volume radius as the per-atom vdW, drops the solvent
        // probe to zero (so the visible radius == the configured number), shows
        // surface, applies transparency, and is disabled by default.
        PocketGridPymolRenderer.render(tinyGrid(), VOL_RADIUS, GAUSSIAN_ISO, tempDir.toString(), "test")
        String pml = new File("${tempDir}/visualizations/test_pocket_grid.pml").text

        assertTrue(pml.contains("create pocket_vol_1, pocket_grid_src and resi 1"))
        assertTrue(pml.contains("create pocket_vol_2, pocket_grid_src and resi 2"))
        assertTrue(pml.contains("color pgc_1, pocket_vol_1"))
        assertTrue(pml.contains("set surface_color, pgc_1, pocket_vol_1"))
        assertTrue(pml.contains("alter pocket_vol_*, vdw=1.500"),
                "vdw should equal the configured volumeRadius (1.5 Å here): ${pml}")
        assertTrue(pml.contains("set solvent_radius, 0, pocket_vol_*"),
                "solvent_radius must be zeroed so volumeRadius is the actual visible radius")
        assertTrue(pml.contains("show surface, pocket_vol_*"))
        assertTrue(pml.contains("set transparency,"))
        assertTrue(pml.contains("group pocket_vol_all, pocket_vol_*"),
                "volume layer must be grouped under pocket_vol_all for single-click toggling")
        // `disable pocket_vol_*` shows up in the header comment as a toggle hint —
        // skip comment lines and only forbid it as a standalone command.
        assertFalse(pml.split("\n").any { String line -> line.trim() == "disable pocket_vol_*" },
                "volume layer is ON by default now — no standalone `disable pocket_vol_*` command should be emitted")
    }

    @Test
    void pmlEmitsGaussianDensityLayer() {
        // Each pocket gets a pocket_gauss_N iso-surface object built from a Gaussian
        // density map. The map (named __pocket_gauss_map_N) is hidden, the iso-surface
        // is disabled by default; user opts in via the right-panel eye icon.
        PocketGridPymolRenderer.render(tinyGrid(), VOL_RADIUS, GAUSSIAN_ISO, tempDir.toString(), "test")
        String pml = new File("${tempDir}/visualizations/test_pocket_grid.pml").text

        assertTrue(pml.contains("set gaussian_b_floor,"),
                "gaussian_b_floor must be set so HETATM B=0 atoms produce non-trivial peaks")
        assertTrue(pml.contains("map_new __pocket_gauss_map_1, gaussian, 0.50, pocket_grid_src and resi 1"))
        assertTrue(pml.contains("isosurface pocket_gauss_1, __pocket_gauss_map_1, 0.50"))
        assertTrue(pml.contains("color pgc_1, pocket_gauss_1"))
        assertTrue(pml.contains("disable pocket_gauss_1"),
                "gaussian iso-surface must be disabled by default")
    }

    @Test
    void pmlEmitsAllFourLayerGroups() {
        // Each layer has a parent group (pocket_<layer>_all) for one-click whole-layer
        // toggling — symmetric across the four layer types.
        PocketGridPymolRenderer.render(tinyGrid(), VOL_RADIUS, GAUSSIAN_ISO, tempDir.toString(), "test")
        String pml = new File("${tempDir}/visualizations/test_pocket_grid.pml").text

        assertTrue(pml.contains("group pocket_grid_all, pocket_grid_*"))
        assertTrue(pml.contains("group pocket_vol_all, pocket_vol_*"))
        assertTrue(pml.contains("group pocket_gauss_all, pocket_gauss_*"))
        assertTrue(pml.contains("group pocket_hull_all, pocket_hull_*"))
    }

    @Test
    void pmlEmitsConvexHullPythonBlock() {
        // The convex-hull layer is computed at PyMOL-load time by an embedded Python
        // block that uses scipy.spatial.ConvexHull. Graceful no-op when scipy is missing.
        PocketGridPymolRenderer.render(tinyGrid(), VOL_RADIUS, GAUSSIAN_ISO, tempDir.toString(), "test")
        String pml = new File("${tempDir}/visualizations/test_pocket_grid.pml").text

        // Helper function defined once
        assertTrue(pml.contains("from scipy.spatial import ConvexHull"),
                "expected scipy.spatial.ConvexHull import in hull block")
        assertTrue(pml.contains("def _pocket_hull(sel, name, r, g, bcol):"))
        assertTrue(pml.contains("cmd.load_cgo(cgo, name)"))
        assertTrue(pml.contains("cmd.disable(name)"),
                "hull objects must be disabled by default")
        // Per-pocket invocations
        assertTrue(pml.contains('_pocket_hull("pocket_grid_src and resi 1", "pocket_hull_1"'))
        assertTrue(pml.contains('_pocket_hull("pocket_grid_src and resi 2", "pocket_hull_2"'))
    }

    @Test
    void pmlVolumeVdwEqualsConfiguredRadiusIndependentOfSpacing() {
        // The volume layer radius is now spacing-independent — the user-facing param
        // means "draw a sphere of this radius around each grid point". Coarse grid
        // (spacing=3.0) and the same volume radius should emit the same vdw value.
        PocketGrid coarse = new PocketGrid(
                new Atoms([new Point(0d, 0d, 0d) as Atom]),
                3.0d, 0d, 0d, 0d,
                makeIdx(0),
                [(1 as Integer): RendererTestFixtures.bits(0)] as LinkedHashMap<Integer, BitSet>)
        PocketGridPymolRenderer.render(coarse, 2.5d, GAUSSIAN_ISO, tempDir.toString(), "coarse")
        String pml = new File("${tempDir}/visualizations/coarse_pocket_grid.pml").text
        assertTrue(pml.contains("alter pocket_vol_*, vdw=2.500"),
                "vdw must equal the volumeRadius arg (2.5), independent of grid spacing 3.0: ${pml}")
    }

    private static LongIntHashMap makeIdx(int idx) {
        LongIntHashMap m = new LongIntHashMap()
        m.put(PocketGrid.pack(0, 0, 0), idx)
        return m
    }

    @Test
    void pmlMakesProteinSemiTransparentForOverlay() {
        // The grid overlay sets a non-zero transparency on the inherited protein
        // surface so the volumetric pocket layer (and the inner pocket cavity) is
        // visible through it. The standalone _pymol.pml leaves protein opaque.
        PocketGridPymolRenderer.render(tinyGrid(), VOL_RADIUS, GAUSSIAN_ISO, tempDir.toString(), "test")
        String pml = new File("${tempDir}/visualizations/test_pocket_grid.pml").text
        assertTrue(pml.contains("set transparency, 0.5, protein"),
                "expected protein transparency override in grid overlay: ${pml}")
        assertTrue(pml.contains("show cartoon, protein"),
                "expected cartoon to be shown under the translucent protein surface")
    }

    @Test
    void pmlNoLongerDuplicatesProteinSurfaceBlock() {
        // The old inline protein/surface/per-pocket-coloring block has been
        // delegated to the @-included standard pml. The grid pml should not
        // re-declare it.
        PocketGridPymolRenderer.render(tinyGrid(), VOL_RADIUS, GAUSSIAN_ISO, tempDir.toString(), "test")
        String pml = new File("${tempDir}/visualizations/test_pocket_grid.pml").text

        assertFalse(pml.contains("show surface, protein"),
                "protein surface block should come from @-included main pml, not inline")
        assertFalse(pml.contains("set_color pcol_"),
                "per-pocket surface coloring should come from @-included main pml")
    }

    @Test
    void pdbHasHetatmPerPointPocketPair() {
        PocketGridPymolRenderer.render(tinyGrid(), VOL_RADIUS, GAUSSIAN_ISO, tempDir.toString(), "test")
        File pdb = new File("${tempDir}/visualizations/data/test_pocket_grid.pdb.gz")

        String text = new GZIPInputStream(new FileInputStream(pdb)).getText('US-ASCII')
        // 2 assigned in pocket 1 + 2 assigned in pocket 2 = 4 HETATM lines
        int hetatmLines = (int) text.readLines().count { String line -> line.startsWith("HETATM") }
        assertEquals(4, hetatmLines)

        // residue numbers must match pocket ranks
        assertTrue(text.contains("STP A   1 "), "pocket 1 residue not present")
        assertTrue(text.contains("STP A   2 "), "pocket 2 residue not present")
    }

    @Test
    void noOpWhenGridHasNoPockets() {
        // Grid with no pockets — renderer should log and not write anything.
        PocketGrid empty = new PocketGrid(
                new Atoms(), 1.0d, 0d, 0d, 0d,
                new LongIntHashMap(),
                new HashMap<Integer, BitSet>())
        PocketGridPymolRenderer.render(empty, VOL_RADIUS, GAUSSIAN_ISO, tempDir.toString(), "empty")

        File pdb = new File("${tempDir}/visualizations/data/empty_pocket_grid.pdb.gz")
        File pml = new File("${tempDir}/visualizations/empty_pocket_grid.pml")
        assertFalse(pdb.exists())
        assertFalse(pml.exists())
    }

    @Test
    void noOpWhenGridIsNull() {
        PocketGridPymolRenderer.render(null, VOL_RADIUS, GAUSSIAN_ISO, tempDir.toString(), "nil")
        // Just verifying no NPE; no files expected.
    }

}
