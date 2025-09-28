package cz.siret.prank.features.implementation.energy2

import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import groovy.transform.CompileStatic

/**
 * SAS point feature: H-bond acceptor probe energy (probe acts as H-bond acceptor).
 * Provides energy statistics from H-bond acceptor probe interactions around each SAS point.
 * Only interacts with protein donor atoms (backbone N, Arg, Lys, Trp, etc.).
 * Units: kcal/mol. More negative = more favorable H-bond donor potential.
 */
@CompileStatic
class HBAcceptorProbeEnergyFeature extends AbstractProbeEnergyFeature {

    static final String NAME = "energy2-hb-acceptor"
    static final String SEC_DATA_KEY = "PP_HB_ACCEPTOR"

    @Override
    ProbeType getProbeType() {
        return ProbeType.HB_ACCEPTOR_SP
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