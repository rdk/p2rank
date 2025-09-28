package cz.siret.prank.features.implementation.energy2.calc

import groovy.transform.CompileStatic

/**
 * Enumeration of supported probe types
 */
@CompileStatic
enum ProbeType {
    NEUTRAL_APOLAR_SP,    // Generic hydrophobe (methyl/ethyl surrogate)
    HB_ACCEPTOR_SP,       // H-bond acceptor probe
    HB_DONOR_SP,          // H-bond donor probe
    AROMATIC_RING_SP,     // Benzene-like hydrophobe
    CATION_SP             // Cationic fragment
}
