package cz.siret.prank.program

import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.Console
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.slf4j.Logger

/**
 *
 */
@Slf4j
@CompileStatic
class P2Rank {

    private static failedFlag = false

    static registerFailure() {
        failedFlag = true
    }

    static isShuttingDown() {
        return failedFlag
    }

    /**
     * Fail conditionally.
     * Throw exception with msg or just log error depending on params.fail_fast
     */
    static void failStatic(String msg, Exception e, Logger llog) throws PrankException {
        if (e != null) {
            llog.error(msg, e)
        } else {
            llog.error(msg)
        }


        if (Params.inst.fail_fast) {
            Console.writeError(msg, e)
            Console.write("Shutting down after an ERROR because fail_fast parameter is set to true.")

            registerFailure()
            throw new PrankException(msg, e)
        } 
    }

    /**
     * Fail conditionally.
     * Throw exception with msg or just log error depending on params.fail_fast
     */
    static void failStatic(String msg, Logger log) throws PrankException {
        failStatic(msg, null, log)
    }
    
}
