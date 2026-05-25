package cz.siret.prank.features.implementation.energy3

import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import groovy.transform.CompileStatic

/**
 * Direct-at-point H-bond acceptor probe energy. No separate surface build.
 */
@CompileStatic
class HBAcceptorDirectFeature extends AbstractDirectProbeEnergyFeature {

    static final String NAME = "e3-hb-acceptor"

    @Override
    ProbeType getProbeType() { return ProbeType.HB_ACCEPTOR_SP }

    @Override
    String getName() { return NAME }
}
