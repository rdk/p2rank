package cz.siret.prank.features.implementation.energy

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.program.params.Params
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static org.junit.jupiter.api.Assertions.*

/**
 * Regression guard for the Cloud-family energy features (MethylEnergyCloud*SF
 * and AbstractProbeEnergyFeature subclasses): the calculator must be built
 * per protein from the current Params, NOT memoized at JVM-lifetime scope.
 *
 * History: an earlier refactor swapped per-protein rebuild for
 * Suppliers.memoize, which silently froze Params for the JVM lifetime and
 * broke grid sweeps (GridOptimizerRoutine mutates Params mid-run between
 * sweep steps). The regression survived 7 commits before a re-audit caught
 * it. This test would have flagged it on the first push.
 *
 * Mechanism: call preProcessProtein twice with different energy_probe_sigma
 * between the calls, on two freshly-loaded proteins, and assert at least
 * some per-point scores differ. If memoization is ever re-introduced, the
 * second protein will see the first protein's frozen sigma and the scores
 * will match.
 */
@Isolated
@ResourceLock("Params")
class MethylEnergyCloudParamsRebuildTest {

    static final String TEST_PROTEIN = "distro/test_data/1fbl.pdb"

    static Params savedParams

    @BeforeAll
    static void snapshot() { savedParams = new Params(); copyEnergyParams(Params.inst, savedParams) }

    @AfterAll
    static void restore() { copyEnergyParams(savedParams, Params.inst) }

    @Test
    void preProcessProteinPicksUpProbeSigmaChangeBetweenProteins() {
        MethylEnergyCloudSF feature = new MethylEnergyCloudSF()

        Params.inst.energy_probe_sigma = 2.0d
        Protein p1 = Protein.load(TEST_PROTEIN)
        feature.preProcessProtein(p1, null)
        List<Double> scoresAt2 = extractScores(p1)

        Params.inst.energy_probe_sigma = 6.0d
        Protein p2 = Protein.load(TEST_PROTEIN)
        feature.preProcessProtein(p2, null)
        List<Double> scoresAt6 = extractScores(p2)

        assertFalse(scoresAt2.isEmpty())
        assertEquals(scoresAt2.size(), scoresAt6.size(),
                "same protein → same probe-point count")
        assertNotEquals(scoresAt2, scoresAt6,
                "energy_probe_sigma change between proteins must affect per-point scores; " +
                "if these match, the calculator is being memoized and Params changes are lost")
    }

    private static List<Double> extractScores(Protein p) {
        ProbePoints pp = (ProbePoints) p.secondaryData.get(MethylEnergyCloudSF.SEC_DATA_KEY)
        return pp.points.list.collect { ((LabeledPoint) it).score }
    }

    /** Copy just the energy_* fields we mutate so the @AfterAll restore is minimal. */
    private static void copyEnergyParams(Params from, Params to) {
        to.energy_probe_sigma = from.energy_probe_sigma
    }
}
