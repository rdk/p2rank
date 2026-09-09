package cz.siret.prank.program.routines.results

import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Guards stat names referenced from Params against drift in the metrics that EvalResults actually produces.
 *
 * A name in 'selected_stats' that nothing produces fails silently: ParamLooper.Step.toCSV() renders the
 * missing value as an empty string, so selected_stats.csv just gets a permanently blank column.
 * DSOR_02_0 / DSOR_02_2 were dead in the default list from 2.4.2-beta.2 (when the criteria were renamed)
 * until they were changed to DSO_02_0 / DSO_02_2.
 */
@CompileStatic
@Isolated
@ResourceLock("Params")
class SelectedStatsNamesTest {

    @BeforeAll
    static void initAll() {
        Params.INSTANCE = new Params()
    }

    @AfterAll
    static void tearDownAll() {
        Params.INSTANCE = new Params()
    }

    /**
     * Names of all metrics produced by an eval run, taken from an empty result.
     * (The key set does not depend on the evaluated data.)
     */
    private static Set<String> producedStatNames() {
        return new EvalResults(1).stats.keySet()
    }

    @Test
    void producedStatNamesAreNotEmpty() {
        // sanity check: guards the two tests below from passing on an empty set
        assertFalse(producedStatNames().isEmpty(), "EvalResults.stats produced no metrics at all")
    }

    @Test
    void everySelectedStatIsProduced() {
        Set<String> produced = producedStatNames()
        List<String> missing = new Params().selected_stats.findAll { String it -> !produced.contains(it) }

        assertTrue(missing.isEmpty(),
                "Params.selected_stats names metrics that EvalResults.stats does not produce: $missing\n" +
                "Such names are written to selected_stats.csv as permanently blank columns.")
    }

    @Test
    void defaultHoptObjectiveIsProduced() {
        String objective = new Params().hopt_objective
        String name = objective.startsWith("-") ? objective.substring(1) : objective  // minus prefix = minimize

        assertTrue(producedStatNames().contains(name),
                "Default Params.hopt_objective '$objective' is not among the metrics produced by EvalResults.stats")
    }

}
