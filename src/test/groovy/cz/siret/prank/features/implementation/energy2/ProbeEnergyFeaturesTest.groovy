package cz.siret.prank.features.implementation.energy2

import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Basic tests for the probe energy feature implementations
 */
@CompileStatic
class ProbeEnergyFeaturesTest {

    @Test
    void testNeutralApolarFeature() {
        NeutralApolarProbeEnergyFeature feature = new NeutralApolarProbeEnergyFeature()

        assertEquals("energy2-neutral-apolar", feature.getName())
        assertEquals("PP_NEUTRAL_APOLAR", feature.getSecondaryDataKey())
        assertEquals(ProbeType.NEUTRAL_APOLAR_SP, feature.getProbeType())

        // Test header
        List<String> header = feature.getHeader()
        assertEquals(9, header.size())
        assertTrue(header.contains("nearest"))
        assertTrue(header.contains("mean1"))
    }

    @Test
    void testAromaticRingFeature() {
        AromaticRingProbeEnergyFeature feature = new AromaticRingProbeEnergyFeature()

        assertEquals("energy2-aromatic-ring", feature.getName())
        assertEquals("PP_AROMATIC_RING", feature.getSecondaryDataKey())
        assertEquals(ProbeType.AROMATIC_RING_SP, feature.getProbeType())
    }

    @Test
    void testHBAcceptorFeature() {
        HBAcceptorProbeEnergyFeature feature = new HBAcceptorProbeEnergyFeature()

        assertEquals("energy2-hb-acceptor", feature.getName())
        assertEquals("PP_HB_ACCEPTOR", feature.getSecondaryDataKey())
        assertEquals(ProbeType.HB_ACCEPTOR_SP, feature.getProbeType())
    }

    @Test
    void testHBDonorFeature() {
        HBDonorProbeEnergyFeature feature = new HBDonorProbeEnergyFeature()

        assertEquals("energy2-hb-donor", feature.getName())
        assertEquals("PP_HB_DONOR", feature.getSecondaryDataKey())
        assertEquals(ProbeType.HB_DONOR_SP, feature.getProbeType())
    }

    @Test
    void testCationFeature() {
        CationProbeEnergyFeature feature = new CationProbeEnergyFeature()

        assertEquals("energy2-cation", feature.getName())
        assertEquals("PP_CATION", feature.getSecondaryDataKey())
        assertEquals(ProbeType.CATION_SP, feature.getProbeType())
    }

    @Test
    void testFeatureInitialization() {
        // Test that all features can be initialized without throwing exceptions
        NeutralApolarProbeEnergyFeature feature1 = new NeutralApolarProbeEnergyFeature()
        AromaticRingProbeEnergyFeature feature2 = new AromaticRingProbeEnergyFeature()
        HBAcceptorProbeEnergyFeature feature3 = new HBAcceptorProbeEnergyFeature()
        HBDonorProbeEnergyFeature feature4 = new HBDonorProbeEnergyFeature()
        CationProbeEnergyFeature feature5 = new CationProbeEnergyFeature()

        // If we get here without exceptions, the test passes
        assertNotNull(feature1)
        assertNotNull(feature2)
        assertNotNull(feature3)
        assertNotNull(feature4)
        assertNotNull(feature5)
    }

    @Test
    void testAllProbeTypesAreCovered() {
        // Ensure we have a feature for each probe type
        Set<ProbeType> implementedTypes = [
            new NeutralApolarProbeEnergyFeature().getProbeType(),
            new AromaticRingProbeEnergyFeature().getProbeType(),
            new HBAcceptorProbeEnergyFeature().getProbeType(),
            new HBDonorProbeEnergyFeature().getProbeType(),
            new CationProbeEnergyFeature().getProbeType()
        ] as Set

        Set<ProbeType> allTypes = EnumSet.allOf(ProbeType.class)

        assertEquals(allTypes, implementedTypes, "All probe types should have corresponding feature implementations")
    }
}