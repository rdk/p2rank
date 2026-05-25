package cz.siret.prank.features.implementation.energy2

import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import groovy.transform.CompileStatic

/**
 * Single-scalar cation probe energy at nearest precomputed SAS probe point.
 */
@CompileStatic
class CationSingleProbeEnergyFeature extends AbstractSingleProbeEnergyFeature {

    static final String NAME = "e2s-cation"
    static final String SEC_DATA_KEY = "PP_CATION"

    @Override
    ProbeType getProbeType() { return ProbeType.CATION_SP }

    @Override
    String getSecondaryDataKey() { return SEC_DATA_KEY }

    @Override
    String getName() { return NAME }
}
