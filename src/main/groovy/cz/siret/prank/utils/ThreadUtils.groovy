package cz.siret.prank.utils

import groovy.transform.CompileStatic

/**
 */
@CompileStatic
class ThreadUtils {

    static void async(Closure closure) {
        Thread.start(closure)
    }

}
