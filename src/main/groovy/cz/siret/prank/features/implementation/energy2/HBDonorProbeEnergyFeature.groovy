package cz.siret.prank.features.implementation.energy2

import cz.siret.prank.features.implementation.energy2.calc.ProbeType
import groovy.transform.CompileStatic

/**
 * SAS point feature: H-bond donor probe energy (probe acts as H-bond donor).
 * Provides energy statistics from H-bond donor probe interactions around each SAS point.
 * Only interacts with protein acceptor atoms (backbone O, Asp, Glu, Asn, Gln, etc.).
 * Units: kcal/mol. More negative = more favorable H-bond acceptor potential.
 */
@CompileStatic
class HBDonorProbeEnergyFeature extends AbstractProbeEnergyFeature {

    static final String NAME = "energy2-hb-donor"
    static final String SEC_DATA_KEY = "PP_HB_DONOR"

    @Override
    ProbeType getProbeType() {
        return ProbeType.HB_DONOR_SP
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