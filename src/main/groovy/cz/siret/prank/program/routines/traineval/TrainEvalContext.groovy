package cz.siret.prank.program.routines.traineval

import groovy.transform.CompileStatic

import javax.annotation.Nullable

/**
 *
 */
@CompileStatic
class TrainEvalContext {

    boolean cacheModels = false

    @Nullable
    ModelCache modelCache

    static TrainEvalContext create() {
        return new TrainEvalContext()
    }
    
}
