package cz.siret.prank.program

import groovy.transform.CompileStatic

@CompileStatic
class PrankException extends RuntimeException {

    PrankException(String var1) {
        super(var1)
    }

    PrankException(String var1, Throwable var2) {
        super(var1, var2)
    }

    PrankException(String var1, Throwable var2, boolean var3, boolean var4) {
        super(var1, var2, var3, var4)
    }

    PrankException() {
    }

    PrankException(Throwable var1) {
        super(var1)
    }

}
