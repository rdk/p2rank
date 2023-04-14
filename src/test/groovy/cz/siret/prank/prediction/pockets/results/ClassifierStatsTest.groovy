package cz.siret.prank.prediction.pockets.results


import cz.siret.prank.prediction.metrics.Metrics
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

@CompileStatic
class ClassifierStatsTest {

    @Test
    void testCalcMCC() {

        System.println Metrics.calcMCC(23861,15594,1411622,109115)

        // TODO add actual test cases for MCC calculation
    }

}
