package cz.siret.prank.utils

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.ATimer.startTimer

/**
 *
 */
@Slf4j
@CompileStatic
class Bench {

    static long timeit(String label, Closure c, boolean doLog) {
        def timer = startTimer()
        c.call()
        long time = timer.time
        if (doLog) log.info("$label: " + time)
        return time
    }

    static long timeit(String label, Closure c) {
        return timeit(label, c, true)
    }

    static long timeit(String label, int reps, Closure c) {
        def timer = startTimer()
        for (int i=0; i!=reps; ++i) {
            timeit("    " + label + " (run ${i+1})", c, reps>1)
        }
        long sum = timer.time
        long avg = (long)(sum/reps)
        //log.info("$label (SUM): " + sum)
        log.warn("$label (AVG): " + avg)
        return avg
    }

}
