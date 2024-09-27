package cz.siret.prank.utils

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.ATimer.startTimer

/**
 * Simple timer for benchmarking
 */
@Slf4j
@CompileStatic
class Bench {

    static long doTimeit(String label, Closure c, boolean doLog) {
        def timer = startTimer()
        c.call()
        long time = timer.time
        if (doLog) log.info("$label: " + time)
        return time
    }

    static long timeitLog(String label, Closure c) {
        return doTimeit(label, c, true)
    }
    static long timeit(String label, Closure c) {
        return doTimeit(label, c, false)
    }

    static long timeit(Closure c) {
        return doTimeit(null, c, false)
    }

    static long timeitLog(String label, int reps, Closure c) {
        return timeitLog(label, reps, false, c)
    }

    static long timeitLogWithHeatup(String label, int reps, Closure c) {
        return timeitLog(label, reps, true, c)
    }

    static long timeitLog(String label, int reps, boolean doHeatup, Closure c) {
        if (doHeatup) {
            doTimeit("    " + label + " (heat-up)", c, true)
        }

        boolean doLog = reps>1 || doHeatup
        long time = 0
        for (int i=0; i!=reps; ++i) {
            time += doTimeit("    " + label + " (run ${i+1})", c, doLog)
            Thread.sleep(50)
        }
        long avg = (long)(time/reps)
        //log.info("$label (SUM): " + sum)
        log.warn("$label (AVG): " + avg)
        return avg
    }

}
