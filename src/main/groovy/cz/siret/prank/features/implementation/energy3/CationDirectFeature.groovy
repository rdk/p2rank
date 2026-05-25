package cz.siret.prank.features.implementation.energy3

import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import groovy.transform.CompileStatic

/**
 * Direct-at-point cation probe energy. No separate surface build.
 */
@CompileStatic
class CationDirectFeature extends AbstractDirectProbeEnergyFeature {

    static final String NAME = "e3-cation"

    @Override
    ProbeType getProbeType() { return ProbeType.CATION_SP }

    @Override
    String getName() { return NAME }
}
