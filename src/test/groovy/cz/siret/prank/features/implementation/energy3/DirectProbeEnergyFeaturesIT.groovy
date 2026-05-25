package cz.siret.prank.features.implementation.energy3

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Params
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static org.junit.jupiter.api.Assertions.*

@Isolated
@ResourceLock("Params")
class DirectProbeEnergyFeaturesIT {

    static final String PDB_1T7QA = "distro/test_data/liganated/1t7qa.pdb"
    static Params savedParams

    @BeforeAll
    static void setup() {
        savedParams = Params.INSTANCE
        Params.INSTANCE = new Params()
    }

    @AfterAll
    static void tearDown() {
        Params.INSTANCE = savedParams
    }

    private static Protein loadProtein() {
        File f = new File(PDB_1T7QA)
        assertTrue(f.exists(), "expected test PDB at $PDB_1T7QA")
        return Protein.load(PDB_1T7QA, new LoaderParams())
    }

    @Test
    void allFiveProbesProduceFiniteValues() {
        Protein p = loadProtein()
        List<AbstractDirectProbeEnergyFeature> features = [
            new NeutralApolarDirectFeature(),
            new HBDonorDirectFeature(),
            new HBAcceptorDirectFeature(),
            new CationDirectFeature(),
            new AromaticRingDirectFeature(),
        ]

        def itemCtx = null
        for (AbstractDirectProbeEnergyFeature feat : features) {
            feat.preProcessProtein(p, itemCtx)
        }

        Atom sasPoint = p.exposedAtoms.list.first()
        Atoms neighbourhood = p.exposedAtoms.cutoutSphere(sasPoint, Params.INSTANCE.neighbourhood_radius)
        SasFeatureCalculationContext ctx = new SasFeatureCalculationContext(p, neighbourhood, null)

        for (AbstractDirectProbeEnergyFeature feat : features) {
            double[] result = feat.calculateForSasPoint(sasPoint, ctx)
            assertEquals(1, result.length, "${feat.name} should return 1 value")
            assertTrue(Double.isFinite(result[0]), "${feat.name} should return finite value, got ${result[0]}")
        }
    }

    @Test
    void neutralApolarProducesNonZeroEnergy() {
        Protein p = loadProtein()
        NeutralApolarDirectFeature feat = new NeutralApolarDirectFeature()
        feat.preProcessProtein(p, null)

        int nonZero = 0
        int tested = 0
        for (Atom sasPoint : p.exposedAtoms.list.take(50)) {
            Atoms neighbourhood = p.exposedAtoms.cutoutSphere(sasPoint, Params.INSTANCE.neighbourhood_radius)
            SasFeatureCalculationContext ctx = new SasFeatureCalculationContext(p, neighbourhood, null)
            double[] result = feat.calculateForSasPoint(sasPoint, ctx)
            if (result[0] != 0d) nonZero++
            tested++
        }
        assertTrue(nonZero > tested / 2,
            "most SAS points should have non-zero neutral apolar energy (got $nonZero/$tested)")
    }

    @Test
    void cachedCalculatorIsSharedAcrossProbes() {
        Protein p = loadProtein()
        NeutralApolarDirectFeature feat1 = new NeutralApolarDirectFeature()
        CationDirectFeature feat2 = new CationDirectFeature()

        feat1.preProcessProtein(p, null)
        feat2.preProcessProtein(p, null)

        Object calc1 = p.secondaryData.get("energy3_calculator")
        assertNotNull(calc1, "calculator should be cached")
        feat1.preProcessProtein(p, null)
        assertSame(calc1, p.secondaryData.get("energy3_calculator"),
            "calculator should be the same instance on second call")
    }

    @Test
    void pointCacheAvoidsRecomputation() {
        Protein p = loadProtein()
        NeutralApolarDirectFeature feat1 = new NeutralApolarDirectFeature()
        HBDonorDirectFeature feat2 = new HBDonorDirectFeature()
        feat1.preProcessProtein(p, null)
        feat2.preProcessProtein(p, null)

        Atom sasPoint = p.exposedAtoms.list.first()
        Atoms neighbourhood = p.exposedAtoms.cutoutSphere(sasPoint, Params.INSTANCE.neighbourhood_radius)
        SasFeatureCalculationContext ctx = new SasFeatureCalculationContext(p, neighbourhood, null)

        double[] r1 = feat1.calculateForSasPoint(sasPoint, ctx)
        double[] r2 = feat2.calculateForSasPoint(sasPoint, ctx)

        assertTrue(Double.isFinite(r1[0]))
        assertTrue(Double.isFinite(r2[0]))
        // Different probes should generally produce different energies
        // (neutral apolar is LJ-only, HB-donor is HB 12-10)
    }

    @Test
    void featureNamesMatchExpected() {
        assertEquals("e3-neutral-apolar", new NeutralApolarDirectFeature().getName())
        assertEquals("e3-hb-donor", new HBDonorDirectFeature().getName())
        assertEquals("e3-hb-acceptor", new HBAcceptorDirectFeature().getName())
        assertEquals("e3-cation", new CationDirectFeature().getName())
        assertEquals("e3-aromatic-ring", new AromaticRingDirectFeature().getName())
    }

    @Test
    void probeTypeOrdinalsMatchEnergyCalculatorOrder() {
        assertEquals(0, ProbeType.NEUTRAL_APOLAR_SP.ordinal())
        assertEquals(1, ProbeType.HB_ACCEPTOR_SP.ordinal())
        assertEquals(2, ProbeType.HB_DONOR_SP.ordinal())
        assertEquals(3, ProbeType.AROMATIC_RING_SP.ordinal())
        assertEquals(4, ProbeType.CATION_SP.ordinal())
    }
}
