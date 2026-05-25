package cz.siret.prank.features.implementation.energy2

import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import groovy.transform.CompileStatic

/**
 * Single-scalar neutral apolar probe energy at nearest precomputed SAS probe point.
 */
@CompileStatic
class NeutralApolarSingleProbeEnergyFeature extends AbstractSingleProbeEnergyFeature {

    static final String NAME = "e2s-neutral-apolar"
    static final String SEC_DATA_KEY = "PP_NEUTRAL_APOLAR"

    @Override
    ProbeType getProbeType() { return ProbeType.NEUTRAL_APOLAR_SP }

    @Override
    String getSecondaryDataKey() { return SEC_DATA_KEY }

    @Override
    String getName() { return NAME }
}
