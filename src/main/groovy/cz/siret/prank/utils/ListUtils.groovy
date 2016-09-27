package cz.siret.prank.utils

import groovy.transform.CompileStatic

@CompileStatic
class ListUtils {

    static <T> List<T> head(int n, List<T> list) {
        if (n>=list.size()) return list
        return list.subList(0, n)
    }

    static <T> List<T> tail(int n, List<T> list) {
        return head(n, list.reverse())
    }

}
