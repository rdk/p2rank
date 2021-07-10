package cz.siret.prank.program.routines.traineval

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue;

/**
 *
 */
@CompileStatic
@Slf4j
class TrainEvalRoutineTest {

    static String data_dir = 'distro/test_data'
    static String out_dir = 'distro/test_output/traineval_test'

    @Test
    void testTrainEval() {

        Params originalParams = (Params) Params.inst.clone()

        try {
            Dataset train = Dataset.loadFromFile("$data_dir/fpocket.ds")
            Dataset eval = Dataset.loadFromFile("$data_dir/test.ds")

            Params.inst.installDir = "distro" // necessary, P2Rank must know where to find score transformer data
            Params.inst.sample_negatives_from_decoys = true
            Params.inst.loop = 1
            Params.inst.classifier = "FasterForest"
            Params.inst.rf_trees = 4
            Params.inst.rf_depth = 9
            Params.inst.fail_fast = true
            LoaderParams.ignoreLigandsSwitch = false

            TrainEvalRoutine routine = new TrainEvalRoutine(out_dir, train, eval)
            routine.collectTrainVectors()
            EvalResults res = routine.trainAndEvalModel()

            log.error("MCC: " + res.stats.MCC)

            assertEquals("Check if processed 5 proteins", 5 as long, res.stats.PROTEINS as long)
            assertTrue(res.stats.POCKETS > 5)
            assertTrue(res.stats.TRAIN_POSITIVES > 10)
            assertTrue(res.stats.TRAIN_NEGATIVES > 10)

            assertTrue("MCC must be > 0.4, actual: ${res.stats.MCC}", res.stats.MCC > 0.4)

            double dca_4_0 = Double.parseDouble(res.stats.DCA_4_0 as String)

            assertTrue("DCA_4_0 must be >= 0.5, actual: $dca_4_0", dca_4_0 >= 0.5)


        } finally {
            Params.INSTANCE = originalParams
            try {
                Futils.delete(out_dir)
            } catch (Exception e) {
                println(e)
            }
        }

    }

    /**
     *
     */
    @Test
    void testTrainEval2() {

        Params originalParams = (Params) Params.inst.clone()

        try {
            Dataset train = Dataset.loadFromFile("$data_dir/test.ds")
            Dataset eval = Dataset.loadFromFile("$data_dir/test.ds")

            Params.inst.installDir = "distro" // necessary, P2Rank must know where to find score transformer data
            Params.inst.sample_negatives_from_decoys = false // different code path ... train on all protein surface
            Params.inst.loop = 1
            Params.inst.classifier = "FastRandomForest"
            Params.inst.rf_trees = 8
            Params.inst.rf_depth = 10
            Params.inst.fail_fast = true
            LoaderParams.ignoreLigandsSwitch = false

            TrainEvalRoutine routine = new TrainEvalRoutine(out_dir, train, eval)
            routine.collectTrainVectors()
            EvalResults res = routine.trainAndEvalModel()

            assertEquals("Check if processed 5 proteins", 5 as long, res.stats.PROTEINS as long)

            assertTrue(res.stats.POCKETS > 5)
            assertTrue(res.stats.TRAIN_POSITIVES > 10)
            assertTrue(res.stats.TRAIN_NEGATIVES > 10)

            assertTrue("MCC must be > 0.5", res.stats.MCC > 0.5)

            double dca_4_0 = Double.parseDouble(res.stats.DCA_4_0 as String)

            assertTrue("DCA_4_0 must be >= 0.5, actual: $dca_4_0", dca_4_0 >= 0.5)


        } finally {
            Params.INSTANCE = originalParams
            try {
                Futils.delete(out_dir)
            } catch (Exception e) {
                println(e)
            }
        }

    }

}