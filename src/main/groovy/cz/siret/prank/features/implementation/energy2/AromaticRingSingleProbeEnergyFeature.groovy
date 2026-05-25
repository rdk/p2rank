package cz.siret.prank.features.implementation.energy2

import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import groovy.transform.CompileStatic

/**
 * Single-scalar aromatic ring probe energy at nearest precomputed SAS probe point.
 */
@CompileStatic
class AromaticRingSingleProbeEnergyFeature extends AbstractSingleProbeEnergyFeature {

    static final String NAME = "e2s-aromatic-ring"
    static final String SEC_DATA_KEY = "PP_AROMATIC_RING"

    @Override
    ProbeType getProbeType() { return ProbeType.AROMATIC_RING_SP }

    @Override
    String getSecondaryDataKey() { return SEC_DATA_KEY }

    @Override
    String getName() { return NAME }
}
