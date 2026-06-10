package cz.siret.prank.program.ml

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.program.ml.FlattenComparison.VariantEval
import cz.siret.prank.program.params.ConfigLoader
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.Futils
import groovy.util.logging.Slf4j
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static org.junit.jupiter.api.Assertions.*

/**
 * G0 — end-to-end pocket-level gate: flatten the shipped (faithful) default model to SoaLegacy and
 * Int16LeafSoa and verify the variants reproduce the default model's binding-site detection.
 *
 *  - SoaLegacy is bit-exact to LegacyFlat => identical pockets => identical DCA + point AUC.
 *  - Int16LeafSoa is ranking-equivalent (int16-quantized leaves) => point AUC within ~1e-3 and no DCA
 *    regression beyond one borderline protein.
 *
 * Runs real de-novo prediction on distro/test_data/test.ds (5 liganated proteins), three times (baseline
 * + 2 variants), so it is heavier than the in-memory ModelConverterTest — same weight class as
 * TrainEvalRoutineTest.
 */
@Isolated
@ResourceLock("Params")
@Slf4j
class FlattenGateTest {

    static final String OUT_DIR = "distro/test_output/g0_flatten_test"
    static final String EVAL_DS = "distro/test_data/test.ds"

    @BeforeAll
    static void initAll() {
        Params.INSTANCE = new Params()
        Params.inst.installDir = "distro"                        // P2Rank needs this for model/config/data
        ConfigLoader.overrideConfig(Params.inst, new File("distro/config/default.groovy"))
        Params.inst.visualizations = false
        Params.inst.fail_fast = true
        LoaderParams.ignoreLigandsSwitch = false
    }

    @AfterAll
    static void tearDownAll() {
        Params.INSTANCE = new Params()
        try { Futils.delete(OUT_DIR) } catch (Exception ignored) {}
    }

    @Test
    void faithfulFlattenVariantsMatchDefaultModelPocketRanking() {
        String modelDir = Params.inst.installDir + "/models/" + Params.inst.model
        Model base = Model.loadFromFileOrDir(modelDir)           // unflattened shipped default (faithful)
        Dataset dataset = Dataset.loadFromFile(EVAL_DS)
        assertTrue(dataset.size > 0, "eval dataset must have items")

        List<VariantEval> results = new FlattenComparison().compare(base, dataset,
                FlattenComparison.DEFAULT_FAITHFUL_TARGETS, OUT_DIR)

        VariantEval baseR = results.find { it.baseline }
        VariantEval soa   = results.find { it.variant == "SoaLegacyFlatBinaryForest" }
        VariantEval int16 = results.find { it.variant == "Int16LeafSoaLegacyFlatBinaryForest" }
        assertNotNull(baseR); assertNotNull(soa); assertNotNull(int16)

        // sanity: the baseline actually predicted (AUC well above chance)
        assertTrue(baseR.point_AUC > 0.5d, "baseline point AUC should be > 0.5, was ${baseR.point_AUC}")

        // SoaLegacy is BIT-EXACT to the (legacy) default => identical pocket-level results.
        assertEquals(baseR.dca_4_0, soa.dca_4_0, 1e-9d, "SoaLegacy DCA_4_0 must equal the default model")
        assertEquals(baseR.dca_4_2, soa.dca_4_2, 1e-9d, "SoaLegacy DCA_4_2 must equal the default model")
        assertEquals(baseR.point_AUC, soa.point_AUC, 1e-9d, "SoaLegacy point AUC must equal the default model")

        // Int16LeafSoa is ranking-equivalent (approximate): point AUC ~identical, no DCA regression beyond
        // one borderline protein on this tiny 5-protein set.
        assertTrue(Math.abs(baseR.point_AUC - int16.point_AUC) < 1e-3d,
                "Int16LeafSoa point AUC must be ~identical to default (delta=${Math.abs(baseR.point_AUC - int16.point_AUC)})")
        assertTrue(int16.dca_4_0 >= baseR.dca_4_0 - 0.2d,
                "Int16LeafSoa DCA_4_0 must not regress materially vs default (${int16.dca_4_0} vs ${baseR.dca_4_0})")
    }
}
