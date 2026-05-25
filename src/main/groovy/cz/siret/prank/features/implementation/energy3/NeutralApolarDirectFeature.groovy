package cz.siret.prank.features.implementation.energy3

import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import groovy.transform.CompileStatic

/**
 * Direct-at-point neutral apolar probe energy. No separate surface build.
 */
@CompileStatic
class NeutralApolarDirectFeature extends AbstractDirectProbeEnergyFeature {

    static final String NAME = "e3-neutral-apolar"

    @Override
    ProbeType getProbeType() { return ProbeType.NEUTRAL_APOLAR_SP }

    @Override
    String getName() { return NAME }
}
