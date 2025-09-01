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


    static double[] average(List<SimpleSecStructType> types) {
        double[] res = new double[HEADER.size()]

        if (types == null || types.empty) return res
        types = types.findAll { it != null }.asList()
        if (types.empty) return res


        for (SimpleSecStructType type : types) {
            res[type.ordinal()] += 1d
        }

        int n = types.size()
        for (int i=0; i!=res.length; i++) {
            res[i] /= n
        }

        res
    }
    
}
