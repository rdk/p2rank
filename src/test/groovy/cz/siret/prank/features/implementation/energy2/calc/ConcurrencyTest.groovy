package cz.siret.prank.features.implementation.energy2.calc

import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.AtomImpl
import org.biojava.nbio.structure.Element
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.AminoAcidImpl
import org.junit.jupiter.api.Test

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import static org.junit.jupiter.api.Assertions.*

/**
 * Test concurrent access to EnergyCalculator to ensure thread safety
 */
@CompileStatic
class ConcurrencyTest {

    @Test
    void testConcurrentEnergyCalculation() {
        EnergyCalculatorConfig config = new EnergyCalculatorConfig()
        EnergyCalculator calculator = new EnergyCalculator(config)

        // Create test atoms
        Atom point = createAtom("C", 0, 0, 0)
        Atoms neighbors = new Atoms([
            createAtom("N", 3.0, 0, 0, "GLY", "N"),
            createAtom("O", 0, 3.0, 0, "GLY", "O"),
            createAtom("C", 0, 0, 4.0, "ALA", "CB"),
            createAtom("N", 2.0, 2.0, 0, "ARG", "NE"),
            createAtom("O", 2.0, 0, 2.0, "ASP", "OD1")
        ])

        int numThreads = 10
        int numOperationsPerThread = 100
        CountDownLatch latch = new CountDownLatch(numThreads)
        ExecutorService executor = Executors.newFixedThreadPool(numThreads)

        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>())

        // Submit concurrent tasks
        for (int i = 0; i < numThreads; i++) {
            executor.submit({
                try {
                    for (int j = 0; j < numOperationsPerThread; j++) {
                        List<Double> energies = calculator.computeEnergyForPoint(point, neighbors)

                        // Verify we get expected number of energies
                        assertEquals(ProbeType.values().length, energies.size())

                        // Verify energies are finite numbers
                        for (Double energy : energies) {
                            assertFalse(energy.isNaN(), "Energy should not be NaN")
                            assertFalse(energy.isInfinite(), "Energy should not be infinite")
                        }
                    }
                } catch (Exception e) {
                    exceptions.add(e)
                } finally {
                    latch.countDown()
                }
            })
        }

        // Wait for all threads to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Threads should complete within 30 seconds")

        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor should shutdown within 5 seconds")

        // Verify no exceptions occurred
        if (!exceptions.isEmpty()) {
            Exception firstException = exceptions.get(0)
            fail("Concurrent execution failed with ${exceptions.size()} exceptions. First: ${firstException.message}")
        }
    }

    // Helper method to create test atoms
    private Atom createAtom(String element, double x, double y, double z, String resName = "GLY", String atomName = "CA") {
        Atom atom = new AtomImpl()
        atom.setElement(Element.valueOfIgnoreCase(element))
        atom.setX(x)
        atom.setY(y)
        atom.setZ(z)
        atom.setName(atomName)

        Group group = new AminoAcidImpl()
        group.setPDBName(resName)
        atom.setGroup(group)

        return atom
    }
}