package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.*
import cz.siret.prank.domain.labeling.BinaryLabeling
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.implementation.conservation.ConservationScore
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Chain

import javax.annotation.Nonnull
import javax.annotation.Nullable
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Function

/**
 * Base class for prediction loaders (parsers for predictions produced by pocket prediction tools)
 */
@Slf4j
@CompileStatic
abstract class PredictionLoader implements Parametrized {

    /**
     * @param predictionOutputFile main pocket prediction output file
     * @param protein to which this prediction is related. may be null!
     * @return
     */
    abstract Prediction loadPrediction(String predictionOutputFile,
                                       @Nullable Protein liganatedProtein)

//    /**
//     * used when running 'prank rescore' on a dataset with one column 'predictionOutputFile'
//     * @param predictionOutputFile
//     * @return
//     */
//    Prediction loadPredictionWithoutProtein(String predictionOutputFile) {
//        loadPrediction(predictionOutputFile, null)
//    }

}
