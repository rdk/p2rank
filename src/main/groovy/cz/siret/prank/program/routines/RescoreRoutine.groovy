package cz.siret.prank.program.routines

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.program.PrankException
import cz.siret.prank.score.PocketRescorer
import cz.siret.prank.score.WekaSumRescorer
import cz.siret.prank.score.results.RescoringSummary
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.WekaUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import weka.classifiers.Classifier

import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Futils.mkdirs
import static cz.siret.prank.utils.Futils.writeFile

/**
 * EvalRoutine for rescoring pockets found by other methods (Fpocket, ConCavity) ... PRANK.
 */
@Slf4j
@CompileStatic
class RescoreRoutine extends Routine {

    Dataset dataset
    String modelf

    RescoreRoutine(Dataset dataSet, String modelf, String outdir) {
        super(outdir)
        this.dataset = dataSet
        this.modelf = modelf
    }

    Dataset.Result execute() {
        def timer = startTimer()

        mkdirs(outdir)
        writeParams(outdir)

        write "rescoring pockets on proteins from dataset [$dataset.name]"

        if (!(dataset.header.contains("prediction") && dataset.header.contains("protein"))) {
            throw new PrankException("Dataset must contain 'protein' and 'prediction' columns!")
        }

        log.info "outdir: $outdir"

        Classifier classifier = WekaUtils.loadClassifier(modelf)
        WekaUtils.disableParallelism(classifier)

        FeatureExtractor extractor = FeatureExtractor.createFactory()

        Dataset.Result result = dataset.processItems(params.parallel, new Dataset.Processor() {
            void processItem(Dataset.Item item) {
                PredictionPair pair = item.predictionPair
                Prediction prediction = pair.prediction

                PocketRescorer rescorer = new  WekaSumRescorer(classifier, extractor)
                rescorer.reorderPockets(prediction, item.context)

                RescoringSummary rsum = new RescoringSummary(prediction)
                writeFile "$outdir/${item.label}_rescored.csv", rsum.toCSV()
                log.info "\n\nRescored pockets for [$item.label]: \n\n" + rsum.toTable() + "\n"

            }
        })

        write "rescoring finished in $timer.formatted"
        write "results saved to directory [${Futils.absPath(outdir)}]"

        return result
    }

}
