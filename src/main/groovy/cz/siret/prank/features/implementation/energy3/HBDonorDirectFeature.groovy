package cz.siret.prank.features.implementation.energy3

import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import groovy.transform.CompileStatic

/**
 * Direct-at-point H-bond donor probe energy. No separate surface build.
 */
@CompileStatic
class HBDonorDirectFeature extends AbstractDirectProbeEnergyFeature {

    static final String NAME = "e3-hb-donor"

    @Override
    ProbeType getProbeType() { return ProbeType.HB_DONOR_SP }

    @Override
    String getName() { return NAME }
}
