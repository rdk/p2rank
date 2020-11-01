package cz.siret.prank.program

import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import org.slf4j.Logger

/**
 *
 */
@CompileStatic
class P2Rank {

    /**
     * Fail conditionally.
     * Throw exception with msg or just log error depending on params.fail_fast
     */
    static void failStatic(String msg, Exception e, Logger log) throws PrankException {
        if (Params.inst.fail_fast) {
            throw new PrankException(msg, e)
        } else {
            log.error(msg, e)
        }
    }

    /**
     * Fail conditionally.
     * Throw exception with msg or just log error depending on params.fail_fast
     */
    static void failStatic(String msg, Logger log) throws PrankException {
        if (Params.inst.fail_fast) {
            throw new PrankException(msg)
        } else {
            log.error(msg)
        }
    }
    
}
