package cz.siret.prank.program.params

/**
 * provides params property for easy access to global parameters
 */
trait Parametrized {

    Params getParams() {
        return Params.INSTANCE
    }

}