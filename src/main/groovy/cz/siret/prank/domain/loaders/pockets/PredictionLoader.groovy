package cz.siret.prank.domain.loaders.pockets


import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.transform.GeometricTransformation
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.annotation.Nullable

/**
 * Base class for prediction loaders (parsers for predictions produced by pocket prediction tools)
 */
@Slf4j
@CompileStatic
abstract class PredictionLoader implements Parametrized {

    @Nullable GeometricTransformation transformation


    PredictionLoader withTransformation(@Nullable GeometricTransformation transformation) {
        this.transformation = transformation
        return this
    }

    boolean hasTransformation() {
        return transformation != null
    }

    /**
     * @param predictionOutputFile main pocket prediction output file
     * @param protein to which this prediction is related. may be null!
     * @return
     */
    abstract Prediction loadPrediction(String predictionOutputFile, @Nullable Protein queryProtein)


}
