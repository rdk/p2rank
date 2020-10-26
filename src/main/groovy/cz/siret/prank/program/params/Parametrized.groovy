package cz.siret.prank.program.params

import cz.siret.prank.program.PrankException
import org.slf4j.Logger

/**
 * provides params property for easy access to global parameters
 */
trait Parametrized {

    Params getParams() {
        return Params.INSTANCE
    }

    /**
     * Throw exception with msg or just log error depending on params.fail_fast
     */
    void fail(String msg, Exception e, Logger log) throws PrankException {
        if (params.fail_fast) {
            throw new PrankException(msg, e)
        } else {
            log.error(msg, e)
        }
    }

}