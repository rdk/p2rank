package cz.siret.prank.features.implementation.secstruct

import groovy.transform.CompileStatic
import org.biojava.nbio.structure.secstruc.SecStrucType

/**
 *
 */
@CompileStatic
class SsHistogram {

    private static List<String> HEADER = SecStrucType.values().collect {
        SecStrucType it -> it.name() }.toList()

    static List<String> getHeader() {
        HEADER
    }

    static encodeOneHotInplace(SecStrucType type, double[] array, int startIdx) {
        if (type == null) return
        array[startIdx+type.ordinal()] = 1d
    }

    static double[] average(List<SecStrucType> types) {
        double[] res = new double[HEADER.size()]

        for (SecStrucType type : types) {
            res[type.ordinal()] += 1d
        }

        int n = types.size()
        for (int i=0; i!=res.length; i++) {
            res[i] /= n
        }

        res
    }
    
}
