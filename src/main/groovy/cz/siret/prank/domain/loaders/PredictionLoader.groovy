package cz.siret.prank.domain.loaders

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
     * @param predictionOutputFile main pocket prediction output file
     * @return
     */
    PredictionPair loadPredictionPair(String ligandedPdbFile, String predictionOutputFile) {
        File ligf = new File(ligandedPdbFile)

        PredictionPair res = new PredictionPair()
        res.name = ligf.name
        res.liganatedProtein = Protein.load(ligandedPdbFile, loaderParams)

        if (predictionOutputFile!=null) {
            res.prediction = loadPrediction(predictionOutputFile)
        } else {
            res.prediction = new Prediction(res.liganatedProtein, [])
        }

        return res
    }

    /**
     * @param predictionOutputFile main pocket prediction output file
     * @return
     */
    abstract Prediction loadPrediction(String predictionOutputFile)

}
