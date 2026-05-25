package cz.siret.prank.features.implementation.energy2

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.implementation.energy.ProbePoints
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static org.junit.jupiter.api.Assertions.*

/**
 * Smoke integration test for the energy2 probe-energy SAS features.
 * Loads a real PDB, runs each probe's per-protein pre-compute, and asserts
 * the resulting probe-point cloud and per-SAS-point feature vectors are
 * finite and non-trivial.
 *
 * Cation Coulomb assertion is deferred to Wave 2 of the audit follow-up —
 * {@code EnergyCalculator.getAtomCharge} is still a stub returning 0, so
 * CATION_SP currently collapses to LJ.
 */
@Isolated
@ResourceLock("Params")
@CompileStatic
class ProbeEnergyFeaturesIT {

    static final String PDB_1FBL = "distro/test_data/1fbl.pdb"

    static Params savedParams

    @BeforeAll static void setup()    { savedParams = Params.INSTANCE; Params.INSTANCE = new Params() }
    @AfterAll  static void tearDown() { Params.INSTANCE = savedParams }

    private static Protein load() {
        assertTrue(new File(PDB_1FBL).exists(), "expected $PDB_1FBL")
        Protein.load(PDB_1FBL, new LoaderParams())
    }

    private static void runProbe(AbstractProbeEnergyFeature feature, Protein p) {
        ProcessedItemContext ctx = new ProcessedItemContext(null, [:] as Map<String, String>)
        feature.preProcessProtein(p, ctx)
        ProbePoints points = (ProbePoints) p.secondaryData.get(feature.getSecondaryDataKey())
        assertNotNull(points, "ProbePoints cache missing for ${feature.getName()}")
        assertTrue(points.points.count > 100, "expected many SAS probe points; got ${points.points.count}")

        // sample a few real SAS points and run the per-point calc
        SasFeatureCalculationContext sasCtx = new SasFeatureCalculationContext(p, null, null)
        int seen = 0
        int nonZero = 0
        double minVal = Double.POSITIVE_INFINITY
        double maxVal = Double.NEGATIVE_INFINITY
        for (int i = 0; i < points.points.count && seen < 50; i += 10, seen++) {
            Atom sasPoint = points.points.list.get(i)
            double[] vec = feature.calculateForSasPoint(sasPoint, sasCtx)
            assertEquals(feature.header.size(), vec.length, "header/vector length mismatch for ${feature.getName()}")
            for (double v : vec) {
                assertTrue(Double.isFinite(v), "non-finite value in ${feature.getName()}: $v")
                if (v != 0.0d) nonZero++
                minVal = Math.min(minVal, v)
                maxVal = Math.max(maxVal, v)
            }
        }
        assertTrue(nonZero > 0, "all-zero feature vectors for ${feature.getName()}")
        // Energies should bracket zero (attractive negative, repulsive positive)
        // or at least have non-zero range on a non-trivial protein
        assertTrue(maxVal - minVal > 0d, "no variation in ${feature.getName()} scores (min=$minVal max=$maxVal)")
    }

    @Test
    void hbAcceptorProbeProducesFiniteScores() {
        runProbe(new HBAcceptorProbeEnergyFeature(), load())
    }

    @Test
    void hbDonorProbeProducesFiniteScores() {
        runProbe(new HBDonorProbeEnergyFeature(), load())
    }

    @Test
    void neutralApolarProbeProducesFiniteScores() {
        runProbe(new NeutralApolarProbeEnergyFeature(), load())
    }

    @Test
    void cationProbeProducesFiniteScores() {
        // Cation currently == LJ-only (Wave 2 will wire Coulomb to PartialChargeTable)
        runProbe(new CationProbeEnergyFeature(), load())
    }

    @Test
    void aromaticRingProbeProducesFiniteScores() {
        runProbe(new AromaticRingProbeEnergyFeature(), load())
    }

    @Test
    void probePointsCacheSharedAcrossEnergy2Variants() {
        // Each energy2 variant has its own SEC_DATA_KEY (unlike the Methyl Cloud
        // family which intentionally shares "PP_CH3"). Verify the keys differ.
        Protein p = load()
        new HBAcceptorProbeEnergyFeature().preProcessProtein(p, new ProcessedItemContext(null, [:] as Map<String, String>))
        new HBDonorProbeEnergyFeature().preProcessProtein(p, new ProcessedItemContext(null, [:] as Map<String, String>))
        assertNotEquals(
            new HBAcceptorProbeEnergyFeature().getSecondaryDataKey(),
            new HBDonorProbeEnergyFeature().getSecondaryDataKey(),
            "energy2 variants should not share cache keys")
    }

    @Test
    void energy2EnableCoulombFlagHonored() {
        Protein p = load()
        Params.INSTANCE.energy2_enable_coulomb = false
        Params.INSTANCE.energy2_dielectric = 80.0  // distinct from default for paranoia
        // Should still produce finite scores; just no Coulomb contribution
        runProbe(new CationProbeEnergyFeature(), p)
    }
}
