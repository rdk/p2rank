package cz.siret.prank.score.results

import org.junit.Test

class ClassifierStatsTest {

    @Test
    void testCalcMCC() {
        ClassifierStats stats = new ClassifierStats()

        System.println stats.calcMCC(23861,15594,1411622,109115)
    }

}
