package cz.siret.prank.features.implementation.secstruct

import groovy.transform.CompileStatic

/**
 *
 */
@CompileStatic
class SsSimpleHistogram {

    private static final List<String> HEADER = SimpleSecStructType.values().collect { it.name() }.toList()

    static List<String> getHeader() {
        HEADER
    }

    static encodeOneHotInplace(SimpleSecStructType type, double[] array, int startIdx) {
        if (type == null) return
        array[startIdx+type.ordinal()] = 1d
    }
    
}
