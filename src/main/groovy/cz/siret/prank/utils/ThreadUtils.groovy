package cz.siret.prank.utils

/**
 */
class ThreadUtils {

    static void async(Closure closure) {
        Thread.start(closure)
    }

}
