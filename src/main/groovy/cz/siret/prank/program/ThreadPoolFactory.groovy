package cz.siret.prank.program

import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import jsr166y.ForkJoinPool

@CompileStatic
class ThreadPoolFactory implements Parametrized {

    private static ForkJoinPool POOL

    synchronized static ForkJoinPool getPool() {
        if (POOL==null) {
            POOL = new ForkJoinPool(Params.inst.threads)
        }
        return POOL
    }

}
