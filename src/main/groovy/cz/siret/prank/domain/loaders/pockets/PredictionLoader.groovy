package cz.siret.prank.domain.loaders.pockets

import com.sun.istack.internal.Nullable
import cz.siret.prank.domain.*
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.implementation.conservation.ConservationScore
import cz.siret.prank.utils.Futils
import groovy.util.logging.Slf4j

import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Function

/**
 * Base class for prediction loaders (parsers for predictions produced by pocket prediction tools)
 * Also serves as a protein/dataset-item loader even if there is no prediction (TODO: refactor).
 */
@Slf4j
abstract class PredictionLoader {

    LoaderParams loaderParams = new LoaderParams()

    /**
     *
     * @param queryProteinFile path to protein (could be just query pdb file with no ligands when rescoring, or control protein with ligands on evaluation)
     * @param predictionOutputFile main pocket prediction output file (from the second column in the dataset file)
     * @return
     */
    PredictionPair loadPredictionPair(String queryProteinFile, String predictionOutputFile,
                                      ProcessedItemContext itemContext) {
        File protf = new File(queryProteinFile)

        PredictionPair res = new PredictionPair()
        res.name = protf.name

        if (loaderParams.load_only_specified_chains) {
            res.protein = Protein.loadReduced(queryProteinFile, loaderParams, itemContext.item.getChains())
        } else {
            res.protein = Protein.load(queryProteinFile, loaderParams)
        }

        if (predictionOutputFile != null) {
            res.prediction = loadPrediction(predictionOutputFile, res.protein)
        } else {
            res.prediction = new Prediction(res.protein, [])
        }

        // TODO: move conservation related stuff to feature implementation
        loadConservationScores(queryProteinFile, itemContext, res)

        
        return res
    }

    private loadConservationScores(String queryProteinFile, ProcessedItemContext itemContext, PredictionPair res) {
        Path parentDir = Paths.get(queryProteinFile).parent
        if (loaderParams.load_conservation_paths) {
            if (itemContext.datsetColumnValues.get(Dataset.COLUMN_CONSERVATION_FILES_PATTERN) == null) {
                log.info("Setting conservation path. Origin: {}", loaderParams.conservation_origin)
                Function<String, File> conserPathForChain = { String chainId ->
                    parentDir.resolve(ConservationScore.scoreFileForPdbFile(
                            Futils.shortName(queryProteinFile), chainId,
                            loaderParams.conservation_origin)).toFile()
                }
                itemContext.auxData.put(ConservationScore.conservationScoreKey, conserPathForChain)
            } else {
                String pattern = itemContext.datsetColumnValues.get(Dataset.COLUMN_CONSERVATION_FILES_PATTERN);
                Function<String, File> conserPathForChain = { String chainId ->
                    parentDir.resolve(pattern.replaceAll("%chainID%", chainId)).toFile()
                }
                itemContext.auxData.put(ConservationScore.conservationScoreKey, conserPathForChain)
            }
            if (loaderParams.load_conservation) {
                res.protein.loadConservationScores()
            }
        }
    }

    /**
     * @param predictionOutputFile main pocket prediction output file
     * @param protein to which this prediction is related. may be null!
     * @return
     */
    abstract Prediction loadPrediction(String predictionOutputFile,
                                       @Nullable Protein liganatedProtein)

    /**
     * used when running 'prank rescore' on a dataset with one column 'predictionOutputFile'
     * @param predictionOutputFile
     * @return
     */
    Prediction loadPredictionWithoutProtein(String predictionOutputFile) {
        loadPrediction(predictionOutputFile, null)
    }

}
