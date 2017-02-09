package cz.siret.prank.domain.loaders

import com.sun.istack.internal.Nullable
import cz.siret.prank.domain.LoaderParams
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.domain.Protein

/**
 * Loader for predictions produced by some pocket prediction tool
 */
abstract class PredictionLoader {

    LoaderParams loaderParams = new LoaderParams()

    /**
     *
     * @param ligandedPdbFile path to control pdb file with ligands
     * @param predictionOutputFile main pocket prediction output file (from the second column in the dataset file)
     * @return
     */
    PredictionPair loadPredictionPair(String liganatedPdbFile, String predictionOutputFile) {
        File ligf = new File(liganatedPdbFile)

        PredictionPair res = new PredictionPair()
        res.name = ligf.name
        res.liganatedProtein = Protein.load(liganatedPdbFile, loaderParams)

        if (predictionOutputFile!=null) {
            res.prediction = loadPrediction(predictionOutputFile, res.liganatedProtein)
        } else {
            res.prediction = new Prediction(res.liganatedProtein, [])
        }

        return res
    }

    /**
     * @param predictionOutputFile main pocket prediction output file
     * @param protein to which this prediction is related. may be null!
     * @return
     */
    abstract Prediction loadPrediction(String predictionOutputFile, @Nullable Protein liganatedProtein)

    /**
     * used when running 'prank rescore' on a dataset with one column 'predictionOutputFile'
     * @param predictionOutputFile
     * @return
     */
    Prediction loadPredictionWithoutProtein(String predictionOutputFile) {
        loadPrediction(predictionOutputFile, null)
    }

}
