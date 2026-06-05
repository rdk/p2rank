package cz.siret.prank.program.routines.predict.output.grid

import cz.siret.prank.program.routines.predict.output.grid.fill.FillKnobs
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.program.api.PrankFacade
import cz.siret.prank.program.api.PrankPredictor
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import java.nio.file.Path
import java.nio.file.Paths

import static cz.siret.prank.utils.PathUtils.path
import static org.junit.jupiter.api.Assertions.*

/**
 * Real-protein regression for the HARD cross-pocket fill rule enforced in
 * {@link PocketGridBuilder}: a point added to a pocket by filling (beyond that
 * pocket's {@code assignCutoff}, so not in its raw shell) must NOT lie in another
 * pocket's raw shell. Fill may expand into unclaimed space, but must not swallow
 * grid points that are within {@code assignCutoff} of a different pocket.
 *
 * <p>The five structures were selected with {@code prank analyze
 * pocket-grid-overlap} over {@code fptrain.ds} as the worst {@code morph_closing}
 * over-dilation cases (before the rule, morph engulfed neighbouring pockets, e.g.
 * 2W83 ranks 1,5 and 2,4 reached containment 1.0). They are vendored in
 * {@code distro/test_data}.
 *
 * <p>The invariant is checked under {@code morph_closing} — the most aggressive
 * fill — so if it holds there it holds for the gentler closings. For each ordered
 * pocket pair (P, Q): since {@code filled(P) ⊇ raw(P)} and the rule forbids P's
 * fill from entering raw(Q), we must have
 * {@code |filled(P) ∩ raw(Q)| == |raw(P) ∩ raw(Q)|} (P shares with Q's raw shell
 * only the points P already had in its own raw shell — genuine within-cutoff
 * interface sharing, never fill-expansion).
 *
 * <p>Mirrors {@code PocketFilterIntegrationTest}: full prediction pipeline via
 * {@link PrankFacade}, {@code @Isolated} + Params save/restore. Ligands are
 * ignored (predicted pockets are ligand-independent) to keep the test offline.
 */
@Isolated
@ResourceLock("Params")
@CompileStatic
class PocketGridOverlapIntegrationTest {

    static Path installDir = Paths.get("distro").toAbsolutePath()

    /** Most-problematic structures from fptrain (vendored into distro/test_data). */
    private static final List<String> STRUCTURES =
            ['1AFL.pdb', '1HI4.pdb', '184L.pdb', '1CZS.pdb', '2W83.pdb']

    @BeforeAll
    static void init() {
        Params.INSTANCE = new Params()
        // predicted pockets are ligand-independent; skip ligand loading so the test
        // never reaches out to the network for het-group CIFs.
        LoaderParams.ignoreLigandsSwitch = true
    }

    @AfterAll
    static void restore() {
        Params.INSTANCE = new Params()
        LoaderParams.ignoreLigandsSwitch = false
    }

    @TestFactory
    Collection<DynamicTest> fillNeverEntersAnotherPocketsRawShell() {
        STRUCTURES.collect { String pdb ->
            DynamicTest.dynamicTest(pdb, { checkStructure(pdb) } as Executable)
        }
    }

    private void checkStructure(String pdb) {
        Prediction pred = predict(pdb)
        List<? extends Pocket> pockets = pred.outputPockets
        assertTrue(pockets.size() >= 2, "$pdb should predict >= 2 pockets (got ${pockets.size()})")

        PocketGridConfig base = PocketGridConfig.fromParams(Params.inst)
        // Stress the rule under the MOST aggressive fill: legacy morph_closing
        // (min_neighbors=4, max_iters=10) — the runaway-dilation config the rule must tame.
        PocketGridConfig morphCfg = new PocketGridConfig(base.spacing(), base.maxDist(), base.atomBuffer(),
                base.assignCutoff(), base.assignerStrategy(), 'morph_closing', new FillKnobs.Morph(4, 10))
        PocketGrid morph = PocketGridBuilder.build(pred.protein, pockets, morphCfg)
        PocketGrid raw   = PocketGridBuilder.build(pred.protein, pockets, noneConfig(base))

        int n = pockets.size()
        int[] ranks = new int[n]
        long filledTotal = 0, rawTotal = 0
        for (int i = 0; i < n; i++) {
            ranks[i] = pockets[i].rank
            filledTotal += morph.indicesForPocket(ranks[i]).cardinality()
            rawTotal += raw.indicesForPocket(ranks[i]).cardinality()
        }
        // Non-vacuous: morph_closing must actually add fill points, else the invariant is trivial.
        assertTrue(filledTotal > rawTotal,
                "$pdb: morph_closing should add fill points (filled=${filledTotal}, raw=${rawTotal})")

        // The rule: for every ordered pair (P, Q), P's fill adds nothing that is in Q's raw shell.
        for (int i = 0; i < n; i++) {
            BitSet filledP = morph.indicesForPocket(ranks[i])
            BitSet rawP = raw.indicesForPocket(ranks[i])
            for (int j = 0; j < n; j++) {
                if (i == j) continue
                BitSet rawQ = raw.indicesForPocket(ranks[j])
                int filledInQ = PocketGridAnalysis.intersectionCount(filledP, rawQ)
                int rawInQ = PocketGridAnalysis.intersectionCount(rawP, rawQ)
                assertEquals(rawInQ, filledInQ,
                        "$pdb pockets ${ranks[i]}->${ranks[j]}: fill of ${ranks[i]} entered raw shell of " +
                        "${ranks[j]} (filled∩rawQ=${filledInQ}, raw∩rawQ=${rawInQ}); cross-pocket fill rule violated")
            }
        }
    }

    // ---- helpers ----

    private static Prediction predict(String pdbName) {
        PrankPredictor predictor = PrankFacade.createPredictor(installDir)
        return predictor.predict(path(installDir, "test_data", pdbName))
    }

    /** Same geometry/assigner as {@code c}, fill disabled — the raw-shell baseline. */
    private static PocketGridConfig noneConfig(PocketGridConfig c) {
        new PocketGridConfig(c.spacing(), c.maxDist(), c.atomBuffer(), c.assignCutoff(),
                c.assignerStrategy(), 'none', new FillKnobs.None())
    }
}
