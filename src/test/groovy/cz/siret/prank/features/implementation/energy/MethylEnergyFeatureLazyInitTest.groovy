package cz.siret.prank.features.implementation.energy

import cz.siret.prank.program.params.Params
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
