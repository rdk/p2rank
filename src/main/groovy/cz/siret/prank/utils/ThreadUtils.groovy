package cz.siret.prank.utils

/**
 */
class ThreadUtils {

    static Thread runAsync(Closure closure) {

        Thread t = new Thread(new Runnable() {
            @Override
            void run() {
                closure.call()
            }
        }).start()

        return t
    }

}
