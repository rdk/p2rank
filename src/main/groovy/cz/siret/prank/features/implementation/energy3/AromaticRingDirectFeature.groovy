package cz.siret.prank.features.implementation.energy3

import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import groovy.transform.CompileStatic

/**
 * Direct-at-point aromatic ring probe energy. No separate surface build.
 */
@CompileStatic
class AromaticRingDirectFeature extends AbstractDirectProbeEnergyFeature {

    static final String NAME = "e3-aromatic-ring"

    @Override
    ProbeType getProbeType() { return ProbeType.AROMATIC_RING_SP }

    @Override
    String getName() { return NAME }
}
