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

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class PocketGridChimeraXRendererTest {

    private static final double VOL_RADIUS = 1.5d

    @TempDir
    Path tempDir

    private static PocketGrid tinyGrid() {
        return RendererTestFixtures.tinyGrid()
    }

    @Test
    void writesPdbAndCxcAtExpectedPaths() {
        PocketGridChimeraXRenderer.render(tinyGrid(), VOL_RADIUS, tempDir.toString(), "test")

        File pdb = new File("${tempDir}/visualizations/data/test_pocket_grid.pdb.gz")
        File cxc = new File("${tempDir}/visualizations/test_pocket_grid.cxc")
        assertTrue(pdb.exists(), "PDB sidecar not written")
        assertTrue(cxc.exists(), "CXC script not written")
        assertTrue(pdb.length() > 0)
        assertTrue(cxc.length() > 0)
    }

    @Test
    void cxcInheritsStandardSceneAndLoadsBothLayerModels() {
        PocketGridChimeraXRenderer.render(tinyGrid(), VOL_RADIUS, tempDir.toString(), "test")
        String cxc = new File("${tempDir}/visualizations/test_pocket_grid.cxc").text

        assertTrue(cxc.contains("open test_chimerax.cxc"),
                "grid CXC must open the standard cxc to inherit the scene: ${cxc}")
        // Two separate top-level model IDs so the Models-panel checkboxes for spheres vs
        // surfaces are independent. Spheres are split per pocket via per-pocket PDB
        // sidecars opened as submodels (#99.N); surfaces load once at #100 and split
        // via per-pocket `surface` commands.
        assertTrue(cxc.contains("open data/test_pocket_grid_1.pdb.gz format pdb id 99.1"),
                "pocket 1 spheres must load as submodel #99.1 from its per-pocket sidecar")
        assertTrue(cxc.contains("open data/test_pocket_grid_2.pdb.gz format pdb id 99.2"))
        assertTrue(cxc.contains("open data/test_pocket_grid.pdb.gz format pdb id 100"),
                "surfaces layer must load with explicit model id 100 (separate parent for tree grouping)")
        assertTrue(cxc.contains("transparency #1"),
                "protein must be made semi-transparent so layers behind it are visible")
    }

    @Test
    void cxcEmitsPerPocketColorAndTwoLayers() {
        PocketGridChimeraXRenderer.render(tinyGrid(), VOL_RADIUS, tempDir.toString(), "test")
        String cxc = new File("${tempDir}/visualizations/test_pocket_grid.cxc").text

        // Per-pocket color names (shared by both layers)
        assertTrue(cxc.contains("color name pgc_1"))
        assertTrue(cxc.contains("color name pgc_2"))

        // Layer 1 (spheres, model #99): per-pocket submodels (#99.1, #99.2, …) loaded
        // from per-pocket sidecars. ChimeraX `split residues` isn't available for HETATM
        // grids, so we partition on disk instead.
        assertTrue(cxc.contains("color #99.1 pgc_1"),
                "per-pocket submodel coloring (matches the parent-tree-grouping design)")
        assertTrue(cxc.contains("color #99.2 pgc_2"))
        assertTrue(cxc.contains("style #99 sphere"),
                "style propagates from parent #99 to all submodels")
        // Sphere radius scales with grid spacing — test grid uses spacing 1.0 → 0.417
        assertTrue(cxc.contains("size #99 atomRadius 0.417"),
                "sphere radius should scale with spacing (0.417 × 1.0 at test spacing)")
        // Meaningful names in the Models panel
        assertTrue(cxc.contains('rename #99 "pocket grid spheres"'),
                "parent model should be renamed for the Models panel")
        assertTrue(cxc.contains('rename #99.1 "pocket 1"'))
        assertTrue(cxc.contains('rename #99.2 "pocket 2"'))
        assertFalse(cxc.contains("split #99 residues"),
                "ChimeraX `split residues` doesn't work for HETATM PDBs — we partition on disk")

        // Layer 2 (surfaces, model #100): atoms hidden, coloured (so surface inherits),
        // radii bumped to volumeRadius, one surface per pocket.
        assertTrue(cxc.contains("hide #100 atoms"),
                "surfaces-source model's atoms must be hidden — they exist only to parent the surface meshes")
        assertTrue(cxc.contains("color #100:1 pgc_1"))
        assertTrue(cxc.contains("color #100:2 pgc_2"))
        assertTrue(cxc.contains("setattr #100 atoms radius 1.500"))
        // Surface probe and grid spacing both scale with grid spacing.
        // Test grid uses spacing 1.0 → probe 0.333, gridSpacing 0.250.
        assertTrue(cxc.contains("surface #100:1 probeRadius 0.333 gridSpacing 0.250"),
                "surface probe and mesh resolution should both scale with grid spacing")
        assertTrue(cxc.contains("surface #100:2 probeRadius 0.333 gridSpacing 0.250"))
        assertTrue(cxc.contains("transparency #100 20 surfaces"))
        // Surface model names in the Models panel
        assertTrue(cxc.contains('rename #100 "pocket grid surfaces"'))
        assertTrue(cxc.contains('rename #100.1 "pocket 1"'))
        assertTrue(cxc.contains('rename #100.2 "pocket 2"'))

        // No more surfaces created from the spheres model — they'd otherwise become
        // children of #99 and the Models-panel grouping would conflate the layers.
        assertFalse(cxc.contains("surface #99:"),
                "no surfaces should be created from the spheres model (they belong under #100)")
        assertFalse(cxc.contains("enclose true"),
                "'enclose true' is invalid ChimeraX — enclose takes an atom spec, not a boolean")
    }

    @Test
    void cxcDoesNotEmitGaussianIsoLayer() {
        // The PyMOL renderer has a gaussian-iso layer; ChimeraX renderer skips it
        // because `volume gaussian` returns an auto-IDed model that cxc can't reliably
        // style afterward (no inline Python). Verify the broken syntax is gone.
        PocketGridChimeraXRenderer.render(tinyGrid(), VOL_RADIUS, tempDir.toString(), "test")
        String cxc = new File("${tempDir}/visualizations/test_pocket_grid.cxc").text

        // Forbid per-residue volume gaussian (the broken approach). The header comment
        // mentions `volume gaussian #99 sDev` as a power-user manual command, which we
        // tolerate — only the per-residue colon form would be the actual emit.
        assertFalse(cxc.split("\n").any { String line -> line.trim().startsWith("volume gaussian #99:") },
                "ChimeraX cxc should not emit `volume gaussian #99:N` per pocket — broken without Python")
        assertFalse(cxc.contains("#!last"),
                "`#!last` is not a valid ChimeraX selector and shouldn't appear")
    }

    @Test
    void noOpWhenGridHasNoPockets() {
        PocketGrid empty = new PocketGrid(
                new Atoms(), 1.0d, 0d, 0d, 0d,
                new LongIntHashMap(),
                new HashMap<Integer, BitSet>())
        PocketGridChimeraXRenderer.render(empty, VOL_RADIUS, tempDir.toString(), "empty")

        File pdb = new File("${tempDir}/visualizations/data/empty_pocket_grid.pdb.gz")
        File cxc = new File("${tempDir}/visualizations/empty_pocket_grid.cxc")
        assertFalse(pdb.exists())
        assertFalse(cxc.exists())
    }

    @Test
    void noOpWhenGridIsNull() {
        PocketGridChimeraXRenderer.render(null, VOL_RADIUS, tempDir.toString(), "nil")
    }

}
