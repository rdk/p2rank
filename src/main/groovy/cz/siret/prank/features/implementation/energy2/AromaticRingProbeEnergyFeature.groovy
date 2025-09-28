package cz.siret.prank.features.implementation.energy2

import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import groovy.transform.CompileStatic

/**
 * SAS point feature: Aromatic ring probe energy (benzene-like hydrophobe with larger σ).
 * Provides energy statistics from aromatic ring probe interactions around each SAS point.
 * Units: kcal/mol. More negative = more favorable aromatic-aromatic interactions.
 */
@CompileStatic
class AromaticRingProbeEnergyFeature extends AbstractProbeEnergyFeature {

    static final String NAME = "energy2-aromatic-ring"
    static final String SEC_DATA_KEY = "PP_AROMATIC_RING"

    @Override
    ProbeType getProbeType() {
        return ProbeType.AROMATIC_RING_SP
    }

    @Override
    String getSecondaryDataKey() {
        return SEC_DATA_KEY
    }

    @Override
    String getName() {
        return NAME
    }
}