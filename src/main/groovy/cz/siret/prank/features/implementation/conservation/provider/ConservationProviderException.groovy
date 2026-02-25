package cz.siret.prank.features.implementation.conservation.provider

import groovy.transform.CompileStatic

/**
 * Exception thrown by conservation providers on failure to fetch scores.
 */
@CompileStatic
class ConservationProviderException extends RuntimeException {

    ConservationProviderException(String message) {
        super(message)
    }

    ConservationProviderException(String message, Throwable cause) {
        super(message, cause)
    }

}
