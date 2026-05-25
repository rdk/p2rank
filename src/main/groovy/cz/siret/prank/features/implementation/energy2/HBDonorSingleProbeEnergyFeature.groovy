package cz.siret.prank.features.implementation.energy2

import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import groovy.transform.CompileStatic

/**
 * Single-scalar H-bond donor probe energy at nearest precomputed SAS probe point.
 */
@CompileStatic
class HBDonorSingleProbeEnergyFeature extends AbstractSingleProbeEnergyFeature {

    static final String NAME = "e2s-hb-donor"
    static final String SEC_DATA_KEY = "PP_HB_DONOR"

    @Override
    ProbeType getProbeType() { return ProbeType.HB_DONOR_SP }

    @Override
    String getSecondaryDataKey() { return SEC_DATA_KEY }

    @Override
    String getName() { return NAME }
}
