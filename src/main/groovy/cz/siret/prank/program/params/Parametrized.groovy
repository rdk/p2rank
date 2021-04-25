package cz.siret.prank.program.params

import groovy.transform.CompileStatic

/**
 * provides params property for easy access to global parameters
 */
@CompileStatic
trait Parametrized {

    Params getParams() {
        return Params.INSTANCE
    }

}