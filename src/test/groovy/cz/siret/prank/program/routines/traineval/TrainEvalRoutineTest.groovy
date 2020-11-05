package cz.siret.prank.program.routines.traineval

import cz.siret.prank.domain.Dataset
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue;

/**
 *
 */
@CompileStatic
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
            Params.inst.rf_trees = 8
            Params.inst.rf_depth = 10

            TrainEvalRoutine routine = new TrainEvalRoutine(out_dir, train, eval)
            routine.collectTrainVectors()
            EvalResults res = routine.trainAndEvalModel()

            assertEquals(5 as long, res.stats.PROTEINS as long)
            assertTrue("MCC > 0.5", res.stats.MCC > 0.5)

            double dca_4_0 = Double.parseDouble(res.stats.DCA_4_0 as String)

            assertTrue("DCA_4_0 >= 0.5, actual: $dca_4_0", dca_4_0 >= 0.5)
            assertTrue(res.stats.POCKETS > 5)
            assertTrue(res.stats.TRAIN_POSITIVES > 10)
            assertTrue(res.stats.TRAIN_NEGATIVES > 10)

        } finally {
            Params.INSTANCE = originalParams
            Futils.delete(out_dir)
        }

    }

}