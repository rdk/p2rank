package cz.siret.prank.utils

import groovy.transform.CompileStatic

@CompileStatic
class CollectionUtils {

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

    static Map<String, ?> prefixKeys(Map<String, ?> map, String prefix) {
        transformKeys(map, { (String) prefix + it})
    }

}
