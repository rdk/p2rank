package cz.siret.prank.features.implementation.energy2

import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import groovy.transform.CompileStatic

/**
 * SAS point feature: Neutral apolar probe energy (generic hydrophobe, methyl/ethyl surrogate).
 * Provides energy statistics from neutral apolar probe interactions around each SAS point.
 * Units: kcal/mol. More negative = more favorable hydrophobic interactions.
 */
@CompileStatic
class NeutralApolarProbeEnergyFeature extends AbstractProbeEnergyFeature {

    static final String NAME = "energy2-neutral-apolar"
    static final String SEC_DATA_KEY = "PP_NEUTRAL_APOLAR"

    @Override
    ProbeType getProbeType() {
        return ProbeType.NEUTRAL_APOLAR_SP
    }

    @Override
    String getSecondaryDataKey() {
        return SEC_DATA_KEY
    }

    @Override
    String getName() {
        return NAME
    }
}