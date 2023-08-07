package cz.siret.prank.program.routines.traineval

import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.results.EvalResults
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import java.util.function.Consumer

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 *
 */
@Isolated
@ResourceLock("Params")
@CompileStatic
@Slf4j
class TrainEvalApoHoloTest {

    static String data_dir = 'distro/test_data'

//===========================================================================================================//

    @BeforeAll
    static void initAll() {
        Params.INSTANCE = new Params()
    }

    @AfterAll
    static void tearDownAll() {
        Params.INSTANCE = new Params()
    }

//===========================================================================================================//


    private EvalResults doTrainEval(String trainDs, String evalDs, Consumer<Params> paramsSetter) {
        return TrainEvalRoutineTest.doTrainEval(trainDs, evalDs, paramsSetter)
    }

    private void doTestTrainEvalAH(String trainDs, String evalDs, Consumer<Params> paramsSetter) {
        EvalResults res = doTrainEval(trainDs, evalDs, paramsSetter)

        assertEquals(5 as long, res.stats.PROTEINS as long, "Check if processed 5 proteins")
        assertTrue(res.stats.POCKETS > 0, "No pockets predicted")
        assertTrue(res.stats.TRAIN_POSITIVES > 10)
        assertTrue(res.stats.TRAIN_NEGATIVES > 10)

        assertTrue(res.stats.point_MCC > 0.35, "point_MCC must be > 0.35, actual: ${res.stats.point_MCC}")

        double dca_4_0 = Double.parseDouble(res.stats.DCA_4_0 as String)

        assertTrue(dca_4_0 >= 0.2, "DCA_4_0 must be >= 0.2, actual value: $dca_4_0")
    }

//===========================================================================================================//

    @Test
    void testTrainEvalApoHoloZnDataset() {

        String dsPath = "$data_dir/apoholo/zn_apoholo.ds"

        doTestTrainEvalAH(dsPath, dsPath, {
            it.classifier = "FasterForest2"
            it.sample_negatives_from_decoys = false
            it.apoholo_use_for_train = true
            it.apoholo_use_for_eval = true
        })
        doTestTrainEvalAH(dsPath, dsPath, {
            it.classifier = "FasterForest2"
            it.sample_negatives_from_decoys = false
            it.apoholo_use_for_train = false
            it.apoholo_use_for_eval = true
        })
        doTestTrainEvalAH(dsPath, dsPath, {
            it.classifier = "FasterForest2"
            it.sample_negatives_from_decoys = false
            it.apoholo_use_for_train = true
            it.apoholo_use_for_eval = false
        })
        doTestTrainEvalAH(dsPath, dsPath, {
            it.classifier = "FasterForest2"
            it.sample_negatives_from_decoys = false
            it.apoholo_use_for_train = false
            it.apoholo_use_for_eval = false
        })

    }

}