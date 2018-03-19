package cz.siret.prank.utils

import groovy.transform.CompileStatic

/**
 * Collection utils
 */
@CompileStatic
class Cutils {

    static <T> List<T> head(int n, List<T> list) {
        if (n>=list.size()) return list
        return list.subList(0, n)
    }

    static <T> List<T> tail(int n, List<T> list) {
        return head(n, list.reverse())
    }


    static <K, E> Map<K, E> transformKeys(Map<K, E> map, Closure<K> closure) {
        Map<K, E> res = new HashMap<>(map.size())
        map.each {
            res.put( closure.call(it.key), it.value )
        }
        return res
    }

    static <E> Map<String, E> prefixMapKeys(Map<String, E> map, String prefix) {
        transformKeys(map, { (String) prefix + it})
    }

    static double sum(List<Double> list) {
        if (list == null || list.empty) return 0

        double sum = 0
        for (Double d : list) {
            if (d != null) {
                sum += d
            }
        }

        return sum
    }

}
