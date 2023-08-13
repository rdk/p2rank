package cz.siret.prank.program.routines.predict

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.prediction.pockets.rescorers.ModelBasedRescorer
import cz.siret.prank.prediction.pockets.rescorers.PocketRescorer
import cz.siret.prank.prediction.pockets.results.RescoringSummary
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.writeFile

/**
 * EvalRoutine for rescoring pockets found by other methods (Fpocket, ConCavity) ... PRANK.
 */
@Slf4j
@CompileStatic
class RescorePocketsRoutine extends Routine {

    Dataset dataset
    String modelf

    RescorePocketsRoutine(Dataset dataSet, String modelf, String outdir) {
        super(outdir)
        this.dataset = dataSet
        this.modelf = modelf
    }

    Dataset.Result execute() {
        def timer = startTimer()

        mkdirs(outdir)
        writeParams(outdir)

        write "rescoring pockets on proteins from dataset [$dataset.name]"

        if (!(dataset.header.contains(Dataset.COLUMN_PROTEIN) && dataset.header.contains(Dataset.COLUMN_PREDICTION))) {
            throw new PrankException("Dataset must contain '${Dataset.COLUMN_PROTEIN}' and '${Dataset.COLUMN_PREDICTION}' columns!")
        }

        log.info "outdir: $outdir"

        Model model = Model.load(modelf)
        model.disableParalelism()

        if (params.rf_flatten && !params.delete_models) {
            model.saveToFile("$outdir/${model.label}_flattened.model")
        }


        FeatureExtractor extractor = FeatureExtractor.createFactory()

        Dataset.Result result = dataset.processItems { Dataset.Item item ->

            PredictionPair pair = item.predictionPair
            Prediction prediction = pair.prediction

            PocketRescorer rescorer = new  ModelBasedRescorer(model, extractor)
            rescorer.reorderPockets(prediction, item.context)

            RescoringSummary rsum = new RescoringSummary(prediction)
            writeFile "$outdir/${item.label}_rescored.csv", rsum.toCSV()
            log.info "\n\nRescored pockets for [$item.label]: \n\n" + rsum.toTable() + "\n"

        }

        write "rescoring finished in $timer.formatted"
        write "results saved to directory [${Futils.absPath(outdir)}]"

        return result
    }

}
