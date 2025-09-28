package cz.siret.prank.features.implementation.energy2

import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import groovy.transform.CompileStatic

/**
 * SAS point feature: Cation probe energy (positively charged fragment, e.g., methylammonium).
 * Provides energy statistics from cationic probe interactions around each SAS point.
 * Includes both Lennard-Jones and Coulomb electrostatic interactions.
 * Units: kcal/mol. More negative = more favorable cation binding potential.
 */
@CompileStatic
class CationProbeEnergyFeature extends AbstractProbeEnergyFeature {

    static final String NAME = "energy2-cation"
    static final String SEC_DATA_KEY = "PP_CATION"

    @Override
    ProbeType getProbeType() {
        return ProbeType.CATION_SP
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