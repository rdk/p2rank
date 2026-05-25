package cz.siret.prank.features.implementation.energy

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Params
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import static org.junit.jupiter.api.Assertions.*

/**
 * Verifies that the singleton-feature lazy-init pattern (Suppliers.memoize) is
 * race-free under contention. Reproduces the scenario the previous
 * ConcurrencyTest missed: many threads hit the very first call concurrently.
 *
 * Not @CompileStatic so we can read the private memoized field via Groovy's
 * `.@` field accessor.
 */
@Isolated
@ResourceLock("Params")
class MethylEnergyFeatureLazyInitTest {

    static final String PDB_1T7QA = "distro/test_data/liganated/1t7qa.pdb"

    static Params savedParams

    @BeforeAll
    static void setUp() {
        savedParams = Params.INSTANCE
        Params.INSTANCE = new Params()
    }

    @AfterAll
    static void tearDown() {
        Params.INSTANCE = savedParams
    }

    @Test
    void calculatorCutoffUsesWiderRadius() {
        Protein p = Protein.load(PDB_1T7QA, new LoaderParams())
        Atom sasPoint = p.exposedAtoms.list.first()

        Atoms narrowNeighbours = p.exposedAtoms.cutoutSphere(sasPoint, Params.INSTANCE.neighbourhood_radius)
        Atoms wideNeighbours = p.proteinAtoms.cutoutSphere(sasPoint, Params.INSTANCE.energy_rc)

        assertTrue(wideNeighbours.count >= narrowNeighbours.count,
                "energy_rc sphere on proteinAtoms should include at least as many atoms as neighbourhood_radius on exposedAtoms")

        Params.INSTANCE.energy_use_calculator_cutoff = false
        MethylEnergyFeature featureOff = new MethylEnergyFeature()
        SasFeatureCalculationContext ctxDefault = new SasFeatureCalculationContext(p, narrowNeighbours, null)
        double[] defaultEnergy = featureOff.calculateForSasPoint(sasPoint, ctxDefault)

        Params.INSTANCE.energy_use_calculator_cutoff = true
        MethylEnergyFeature featureOn = new MethylEnergyFeature()
        SasFeatureCalculationContext ctxWide = new SasFeatureCalculationContext(p, narrowNeighbours, null)
        double[] wideEnergy = featureOn.calculateForSasPoint(sasPoint, ctxWide)

        assertTrue(Double.isFinite(defaultEnergy[0]), "default path should produce finite energy")
        assertTrue(Double.isFinite(wideEnergy[0]), "calculator-cutoff path should produce finite energy")
        assertNotEquals(defaultEnergy[0], wideEnergy[0], 1e-15,
                "wider radius + all-atom source should produce a different energy value")
    }

    @Test
    void concurrentFirstCallsProduceSingleMemoizedCalculator() {
        MethylEnergyFeature feature = new MethylEnergyFeature()
        int n = 32
        CountDownLatch start = new CountDownLatch(1)
        Set<LJEnergyCalculator> seen = ConcurrentHashMap.newKeySet()
        List<Throwable> errors = Collections.synchronizedList([])

        List<Thread> threads = (1..n).collect {
            Thread.start {
                try {
                    start.await()
                    seen.add(feature.@calculator.get())
                } catch (Throwable t) {
                    errors.add(t)
                }
            }
        }
        start.countDown()
        threads.each { it.join(5_000) }

        assertTrue(errors.empty, "no errors under contention; got: $errors")
        assertEquals(1, seen.size(), "all threads should see the same memoized calculator")
        assertNotNull(seen.iterator().next())
    }
}
