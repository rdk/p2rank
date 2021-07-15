package cz.siret.prank.program


import groovy.transform.CompileStatic
import org.slf4j.Logger

/**
 *
 */
@CompileStatic
trait Failable {

    /**
     * Fail conditionally.
     * Throw exception with msg or just log error depending on params.fail_fast
     */
    void fail(String msg, Exception e, Logger log) throws PrankException {
        P2Rank.failStatic(msg, e, log)
    }

    /**
     * Fail conditionally.
     * Throw exception with msg or just log error depending on params.fail_fast
     */
    void fail(String msg, Logger log) throws PrankException {
        P2Rank.failStatic(msg, log)
    }

}