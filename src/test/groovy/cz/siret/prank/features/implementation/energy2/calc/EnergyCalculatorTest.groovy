package cz.siret.prank.features.implementation.energy2.calc

import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.AtomImpl
import org.biojava.nbio.structure.Element
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.AminoAcidImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach

import static org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for EnergyCalculator - validates each energy term independently
 * and with synthetic geometry according to the spec requirements.
 */
@CompileStatic
class EnergyCalculatorTest {

    EnergyCalculator calculator
    EnergyCalculatorConfig config

    @BeforeEach
    void setup() {
        config = new EnergyCalculatorConfig()
        calculator = new EnergyCalculator(config)
    }

    @Test
    void testConfigurationValidation() {
        assertThrows(IllegalArgumentException.class, {
            new EnergyCalculatorConfig.Builder()
                .rOn(8.0)
                .rCutoff(7.0)  // ron >= rc, should fail
                .build()
        })
    }

    @Test
    void testEmptyNeighborListReturnsZeros() {
        Atom point = createAtom("C", 0, 0, 0)
        Atoms neighbors = new Atoms()

        List<Double> energies = calculator.computeEnergyForPoint(point, neighbors)

        assertEquals(ProbeType.values().length, energies.size())
        for (Double energy : energies) {
            assertEquals(0.0, energy, 1e-10)
        }
    }

    @Test
    void testNeighborsBeyondCutoffAreIgnored() {
        Atom point = createAtom("C", 0, 0, 0)
        Atom farNeighbor = createAtom("C", 15, 0, 0)  // 15 Å away, beyond 9 Å cutoff
        Atoms neighbors = new Atoms([farNeighbor])

        List<Double> energies = calculator.computeEnergyForPoint(point, neighbors)

        for (Double energy : energies) {
            assertEquals(0.0, energy, 1e-10)
        }
    }

    @Test
    void testHydrogenAtomsAreIgnored() {
        Atom point = createAtom("C", 0, 0, 0)
        Atom hydrogen = createAtom("H", 2, 0, 0)
        Atoms neighbors = new Atoms([hydrogen])

        List<Double> energies = calculator.computeEnergyForPoint(point, neighbors)

        for (Double energy : energies) {
            assertEquals(0.0, energy, 1e-10)
        }
    }

    @Test
    void testDistanceClampingPreventsSignularities() {
        Atom point = createAtom("C", 0, 0, 0)
        Atom closeNeighbor = createAtom("C", 0.5, 0, 0)  // 0.5 Å, below rMin=1.8
        Atoms neighbors = new Atoms([closeNeighbor])

        List<Double> energies = calculator.computeEnergyForPoint(point, neighbors)

        for (Double energy : energies) {
            assertFalse(energy.isNaN())
            assertFalse(energy.isInfinite())
        }
    }

    @Test
    void testSmoothSwitchingFunction() {
        Atom point = createAtom("C", 0, 0, 0)

        // At rOn: should have full weight (s=1)
        Atom neighborAtROn = createAtom("C", config.rOn, 0, 0)

        // At rCutoff: should have zero weight (s=0)
        Atom neighborAtRCutoff = createAtom("C", config.rCutoff - 0.01, 0, 0)

        List<Double> energiesAtROn = calculator.computeEnergyForPoint(point, new Atoms([neighborAtROn]))
        List<Double> energiesAtRCutoff = calculator.computeEnergyForPoint(point, new Atoms([neighborAtRCutoff]))

        // Energy at rOn should be stronger (more negative) than near rCutoff
        assertTrue(energiesAtROn[0] < energiesAtRCutoff[0])
    }

    @Test
    void testLJEnergyHasCorrectMinimum() {
        config = new EnergyCalculatorConfig.Builder()
            .selectedProbes(EnumSet.of(ProbeType.NEUTRAL_APOLAR_SP))
            .build()
        calculator = new EnergyCalculator(config)

        Atom point = createAtom("C", 0, 0, 0)

        List<Double> distances = [2.5, 3.0, 3.5, 4.0, 4.5, 5.0] as List<Double>
        List<Double> energies = distances.collect { d ->
            Atom neighbor = createAtom("C", d, 0, 0)
            calculator.computeEnergyForPoint(point, new Atoms([neighbor]))[0]
        }

        double minEnergy = energies.min()
        assertEquals(1, energies.count { it == minEnergy })  // Single minimum
        assertTrue(minEnergy < 0)  // Attractive minimum
    }

    @Test
    void testAromaticRingEnergyCap() {
        config = new EnergyCalculatorConfig.Builder()
            .selectedProbes(EnumSet.of(ProbeType.AROMATIC_RING_SP))
            .build()
        calculator = new EnergyCalculator(config)

        Atom point = createAtom("C", 0, 0, 0)
        Atom closeNeighbor = createAtom("C", 2.0, 0, 0)  // Very close to get strong interaction

        List<Double> energies = calculator.computeEnergyForPoint(point, new Atoms([closeNeighbor]))

        double aromaticEnergy = energies[0]
        double energyCap = config.probeParams[ProbeType.AROMATIC_RING_SP].energyMinCap
        assertTrue(aromaticEnergy >= energyCap)
    }

    @Test
    void testAromaticOnlySkipsNonAromaticNeighbors() {
        config = new EnergyCalculatorConfig.Builder()
            .selectedProbes(EnumSet.of(ProbeType.AROMATIC_RING_SP))
            .aromaticOnly(true)
            .build()
        calculator = new EnergyCalculator(config)

        Atom point = createAtom("C", 0, 0, 0)
        Atom pheAtom = createAtom("C", 3.5, 0, 0, "PHE", "CG")
        Atom alaAtom = createAtom("C", 3.5, 0, 0, "ALA", "CB")

        List<Double> pheEnergies = calculator.computeEnergyForPoint(point, new Atoms([pheAtom]))
        List<Double> alaEnergies = calculator.computeEnergyForPoint(point, new Atoms([alaAtom]))

        assertTrue(pheEnergies[0] != 0d, "aromatic probe should interact with PHE atom")
        assertEquals(0d, alaEnergies[0], 1e-15, "aromatic probe should skip ALA atom when aromaticOnly=true")
    }

    @Test
    void testAromaticOnlyDisabledInteractsWithAllAtoms() {
        config = new EnergyCalculatorConfig.Builder()
            .selectedProbes(EnumSet.of(ProbeType.AROMATIC_RING_SP))
            .aromaticOnly(false)
            .build()
        calculator = new EnergyCalculator(config)

        Atom point = createAtom("C", 0, 0, 0)
        Atom alaAtom = createAtom("C", 3.5, 0, 0, "ALA", "CB")

        List<Double> energies = calculator.computeEnergyForPoint(point, new Atoms([alaAtom]))
        assertTrue(energies[0] != 0d, "aromatic probe should interact with ALA when aromaticOnly=false")
    }

    @Test
    void testAromaticOnlyRecognizesAllAromaticResidues() {
        config = new EnergyCalculatorConfig.Builder()
            .selectedProbes(EnumSet.of(ProbeType.AROMATIC_RING_SP))
            .aromaticOnly(true)
            .build()
        calculator = new EnergyCalculator(config)

        Atom point = createAtom("C", 0, 0, 0)

        for (String res : ["PHE", "TYR", "TRP", "HIS"]) {
            Atom neighbor = createAtom("C", 3.5, 0, 0, res, "CG")
            List<Double> energies = calculator.computeEnergyForPoint(point, new Atoms([neighbor]))
            assertTrue(energies[0] != 0d, "$res should be recognized as aromatic")
        }
    }

    @Test
    void testHBAcceptorProbeOnlyInteractsWithDonors() {
        config = new EnergyCalculatorConfig.Builder()
            .selectedProbes(EnumSet.of(ProbeType.HB_ACCEPTOR_SP))
            .build()
        calculator = new EnergyCalculator(config)

        Atom point = createAtom("C", 0, 0, 0)

        // Donor atom (backbone N)
        Atom donorAtom = createAtom("N", 3.0, 0, 0, "GLY", "N")

        // Non-donor atom (aliphatic C)
        Atom nonDonorAtom = createAtom("C", 3.0, 0, 0, "ALA", "CB")

        List<Double> energiesWithDonor = calculator.computeEnergyForPoint(point, new Atoms([donorAtom]))
        List<Double> energiesWithNonDonor = calculator.computeEnergyForPoint(point, new Atoms([nonDonorAtom]))

        // Should interact with donor but not with non-donor
        assertNotEquals(0.0, energiesWithDonor[0], 1e-10)
        assertEquals(0.0, energiesWithNonDonor[0], 1e-10)
    }

    @Test
    void testHBDonorProbeOnlyInteractsWithAcceptors() {
        config = new EnergyCalculatorConfig.Builder()
            .selectedProbes(EnumSet.of(ProbeType.HB_DONOR_SP))
            .build()
        calculator = new EnergyCalculator(config)

        Atom point = createAtom("C", 0, 0, 0)

        // Acceptor atom (backbone O)
        Atom acceptorAtom = createAtom("O", 3.0, 0, 0, "GLY", "O")

        // Non-acceptor atom (aliphatic C)
        Atom nonAcceptorAtom = createAtom("C", 3.0, 0, 0, "ALA", "CB")

        List<Double> energiesWithAcceptor = calculator.computeEnergyForPoint(point, new Atoms([acceptorAtom]))
        List<Double> energiesWithNonAcceptor = calculator.computeEnergyForPoint(point, new Atoms([nonAcceptorAtom]))

        // Should interact with acceptor but not with non-acceptor
        assertNotEquals(0.0, energiesWithAcceptor[0], 1e-10)
        assertEquals(0.0, energiesWithNonAcceptor[0], 1e-10)
    }

    @Test
    void testCationProbeIncludesBothLJAndCoulombTerms() {
        config = new EnergyCalculatorConfig.Builder()
            .selectedProbes(EnumSet.of(ProbeType.CATION_SP))
            .enableCoulomb(true)
            .build()

        Atom point = createAtom("C", 0, 0, 0)
        Atom neighbor = createAtom("O", 4.0, 0, 0)

        // No supplier → pure LJ (Coulomb collapses to 0)
        double ljOnly = new EnergyCalculator(config)
                .computeEnergyForPoint(point, new Atoms([neighbor])).get(0)

        // With a synthetic −1.0 e charge on the neighbor, CATION_SP (+0.5 e probe)
        // should add an attractive (negative) Coulomb contribution.
        double withCoulomb = new EnergyCalculator(config, { Atom a -> a.is(neighbor) ? -1.0d : 0.0d })
                .computeEnergyForPoint(point, new Atoms([neighbor])).get(0)

        assertNotEquals(0.0d, ljOnly, 1e-10, "pure LJ path should be non-zero")
        assertTrue(withCoulomb < ljOnly,
                "wiring a negative charge should make CATION_SP more attractive: $withCoulomb !< $ljOnly")
    }

    @Test
    void testCationCoulombScalesWithDielectric() {
        // Higher dielectric damps Coulomb → energy closer to LJ-only.
        Atom point = createAtom("C", 0, 0, 0)
        Atom neighbor = createAtom("O", 4.0, 0, 0)
        def supplier = { Atom a -> a.is(neighbor) ? -1.0d : 0.0d } as java.util.function.ToDoubleFunction<Atom>

        EnergyCalculatorConfig lowD = new EnergyCalculatorConfig.Builder()
                .selectedProbes(EnumSet.of(ProbeType.CATION_SP)).enableCoulomb(true)
                .dielectricConstant(4.0).build()
        EnergyCalculatorConfig highD = new EnergyCalculatorConfig.Builder()
                .selectedProbes(EnumSet.of(ProbeType.CATION_SP)).enableCoulomb(true)
                .dielectricConstant(80.0).build()

        double atLowD = new EnergyCalculator(lowD, supplier)
                .computeEnergyForPoint(point, new Atoms([neighbor])).get(0)
        double atHighD = new EnergyCalculator(highD, supplier)
                .computeEnergyForPoint(point, new Atoms([neighbor])).get(0)

        // Attractive: low dielectric is more negative than high dielectric.
        assertTrue(atLowD < atHighD,
                "lower dielectric should give stronger Coulomb attraction: lowD=$atLowD highD=$atHighD")
    }

    @Test
    void testEnableCoulombFlagSuppressesSupplier() {
        EnergyCalculatorConfig cfg = new EnergyCalculatorConfig.Builder()
                .selectedProbes(EnumSet.of(ProbeType.CATION_SP)).enableCoulomb(false).build()
        Atom point = createAtom("C", 0, 0, 0)
        Atom neighbor = createAtom("O", 4.0, 0, 0)

        // Supplier returns charge but enableCoulomb=false should bypass it entirely
        double withFlagOff = new EnergyCalculator(cfg, { Atom a -> -1.0d } as java.util.function.ToDoubleFunction<Atom>)
                .computeEnergyForPoint(point, new Atoms([neighbor])).get(0)
        double pureLj = new EnergyCalculator(cfg)
                .computeEnergyForPoint(point, new Atoms([neighbor])).get(0)

        assertEquals(pureLj, withFlagOff, 1e-12, "enableCoulomb=false should ignore the supplier")
    }

    @Test
    void testConsistencyBatchEqualsSumOfIndividualProbes() {
        Atom point = createAtom("C", 0, 0, 0)
        Atoms neighbors = new Atoms([
            createAtom("N", 3.0, 0, 0, "GLY", "N"),
            createAtom("O", 0, 3.0, 0, "GLY", "O"),
            createAtom("C", 0, 0, 4.0, "ALA", "CB")
        ])

        List<Double> batchEnergies = calculator.computeEnergyForPoint(point, neighbors)

        List<Double> individualEnergies = []
        for (ProbeType probe : ProbeType.values()) {
            EnergyCalculatorConfig singleProbeConfig = new EnergyCalculatorConfig.Builder()
                .selectedProbes(EnumSet.of(probe))
                .build()
            EnergyCalculator singleProbeCalculator = new EnergyCalculator(singleProbeConfig)
            List<Double> singleResult = singleProbeCalculator.computeEnergyForPoint(point, neighbors)
            individualEnergies.add(singleResult[0])
        }

        assertEquals(individualEnergies.size(), batchEnergies.size())
        for (int i = 0; i < batchEnergies.size(); i++) {
            assertEquals(individualEnergies[i], batchEnergies[i], 1e-10)
        }
    }

    @Test
    void testAtomRoleClassification() {
        AtomRole backboneN = AtomRole.classify(createAtom("N", 0, 0, 0, "GLY", "N"))
        AtomRole backboneO = AtomRole.classify(createAtom("O", 0, 0, 0, "GLY", "O"))
        AtomRole argNH1 = AtomRole.classify(createAtom("N", 0, 0, 0, "ARG", "NH1"))
        AtomRole aspOD1 = AtomRole.classify(createAtom("O", 0, 0, 0, "ASP", "OD1"))
        AtomRole alaCB = AtomRole.classify(createAtom("C", 0, 0, 0, "ALA", "CB"))

        assertTrue(backboneN.isDonor && !backboneN.isAcceptor)
        assertTrue(!backboneO.isDonor && backboneO.isAcceptor)
        assertTrue(argNH1.isDonor && !argNH1.isAcceptor)
        assertTrue(!aspOD1.isDonor && aspOD1.isAcceptor)
        assertTrue(!alaCB.isDonor && !alaCB.isAcceptor)
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