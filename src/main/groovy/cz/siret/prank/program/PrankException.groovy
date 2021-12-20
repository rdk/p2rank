package cz.siret.prank.program


import groovy.transform.CompileStatic

@CompileStatic
class PrankException extends RuntimeException {

    PrankException() {
        super()
    }

    PrankException(String message) {
        super(message)
    }

    PrankException(String message, Throwable cause) {
        super(message, cause, false, true)
        if (cause != null) {
            // cause stacktrace is not written
            // so we set it as main
            // (possible groovy/java17 bug)
            setStackTrace(cause.getStackTrace())
        }
    }

    PrankException(Throwable cause) {
        super(cause)
    }

    protected PrankException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace)
    }
    
}
