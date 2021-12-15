package cz.siret.prank.program.routines.traineval

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.junit.Test

import java.util.function.Consumer

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


    private void doTestTrainEval(String trainDs, String evalDs, Consumer<Params> paramsSetter) {
        Params originalParams = (Params) Params.inst.clone()

        try {
            Dataset train = Dataset.loadFromFile(trainDs)
            Dataset eval = Dataset.loadFromFile(evalDs)

            Params.inst.installDir = "distro" // necessary, P2Rank must know where to find score transformer data
            Params.inst.sample_negatives_from_decoys = true
            Params.inst.loop = 1
            Params.inst.classifier = "FasterForest"
            Params.inst.rf_trees = 4
            Params.inst.rf_depth = 9
            Params.inst.fail_fast = true
            LoaderParams.ignoreLigandsSwitch = false

            paramsSetter.accept(Params.inst)

            TrainEvalRoutine routine = new TrainEvalRoutine(out_dir, train, eval)
            routine.collectTrainVectors()
            EvalResults res = routine.trainAndEvalModel()

            assertEquals("Check if processed 5 proteins", 5 as long, res.stats.PROTEINS as long)
            assertTrue(res.stats.POCKETS > 5)
            assertTrue(res.stats.TRAIN_POSITIVES > 10)
            assertTrue(res.stats.TRAIN_NEGATIVES > 10)

            assertTrue("point_MCC must be > 0.35, actual: ${res.stats.point_MCC}", res.stats.point_MCC > 0.35)

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
     * TODO refactor test: merge with doTestTrainEval()
     */
    private void doTestTrainEvalForResidues(String trainDs, String evalDs, Consumer<Params> paramsSetter) {
        Params originalParams = (Params) Params.inst.clone()

        try {
            Dataset train = Dataset.loadFromFile(trainDs)
            Dataset eval = Dataset.loadFromFile(evalDs)

            Params.inst.installDir = "distro" // necessary, P2Rank must know where to find score transformer data
            Params.inst.sample_negatives_from_decoys = true
            Params.inst.loop = 1
            Params.inst.classifier = "FasterForest"
            Params.inst.rf_trees = 4
            Params.inst.rf_depth = 9
            Params.inst.fail_fast = true
            LoaderParams.ignoreLigandsSwitch = false

            paramsSetter.accept(Params.inst)

            TrainEvalRoutine routine = new TrainEvalRoutine(out_dir, train, eval)
            routine.collectTrainVectors()
            EvalResults res = routine.trainAndEvalModel()

            // TODO add sensible tests

            assertTrue("point_MCC must be > 0.1, actual: ${res.stats.point_MCC}", res.stats.point_MCC > 0.1)

        } finally {
            Params.INSTANCE = originalParams
            try {
                Futils.delete(out_dir)
            } catch (Exception e) {
                println(e)
            }
        }

    }

//===========================================================================================================//


    @Test
    void testTrainEvalFF() {

        doTestTrainEval("$data_dir/fpocket.ds", "$data_dir/test.ds", {
            it.classifier = "FasterForest"
        })

    }

    @Test
    void testTrainEvalFRF() {

        doTestTrainEval("$data_dir/fpocket.ds", "$data_dir/test.ds", {
            it.classifier = "FastRandomForest"
        })

    }

    @Test
    void testTrainEvalFF2() {

        doTestTrainEval("$data_dir/fpocket.ds", "$data_dir/test.ds", {
            it.classifier = "FasterForest2"
        })

    }

    @Test
    void testTrainEvalRF() {

        doTestTrainEval("$data_dir/fpocket.ds", "$data_dir/test.ds", {
            it.classifier = "RandomForest"
        })

    }

    @Test
    void testTrainEvalResidueMode() {

        doTestTrainEvalForResidues("$data_dir/fpocket.ds", "$data_dir/test.ds", {
            it.classifier = "FasterForest"
            it.predict_residues = true
        })

    }
    
    @Test
    void testTrainEvalFeatureImportances() {

        doTestTrainEval("$data_dir/fpocket.ds", "$data_dir/test.ds", {
            it.classifier = "FasterForest"
            it.feature_importances = true
        })

    }

}