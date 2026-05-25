package cz.siret.prank.features.implementation.energy2

import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import groovy.transform.CompileStatic

/**
 * Single-scalar H-bond acceptor probe energy at nearest precomputed SAS probe point.
 */
@CompileStatic
class HBAcceptorSingleProbeEnergyFeature extends AbstractSingleProbeEnergyFeature {

    static final String NAME = "e2s-hb-acceptor"
    static final String SEC_DATA_KEY = "PP_HB_ACCEPTOR"

    @Override
    ProbeType getProbeType() { return ProbeType.HB_ACCEPTOR_SP }

    @Override
    String getSecondaryDataKey() { return SEC_DATA_KEY }

    @Override
    String getName() { return NAME }
}
