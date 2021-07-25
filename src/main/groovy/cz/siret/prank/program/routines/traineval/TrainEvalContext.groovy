package cz.siret.prank.program.routines.traineval

import cz.siret.prank.program.ml.FeatureVectors
import groovy.transform.CompileStatic

import javax.annotation.Nullable

/**
 *
 */
@CompileStatic
class TrainEvalContext {

    boolean cacheModels = false
    boolean trainVectorsCollected = false

    @Nullable
    ModelCache modelCache

    FeatureVectors trainVectors

    static TrainEvalContext create() {
        return new TrainEvalContext()
    }
    
}
