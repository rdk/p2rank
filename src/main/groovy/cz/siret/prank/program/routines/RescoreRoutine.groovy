package cz.siret.prank.program.routines

import groovy.util.logging.Slf4j
import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.Prediction
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.score.PocketRescorer
import cz.siret.prank.score.WekaSumRescorer
import cz.siret.prank.score.results.ReorderingSummary
import cz.siret.prank.utils.ATimer
import cz.siret.prank.utils.WekaUtils
import cz.siret.prank.utils.Writable
import cz.siret.prank.utils.futils
import weka.classifiers.Classifier

/**
 * CompositeRoutine for rescoring pockets found by other methods (Fpocket, ConCavity) ... PRANK.
 */
@Slf4j
class RescoreRoutine implements Parametrized, Writable {

    Dataset dataset
    String modelf
    String outdir

    RescoreRoutine(Dataset dataSet, String modelf, String outdir) {
        this.dataset = dataSet
        this.modelf = modelf
        this.outdir = outdir
    }

    Dataset.Result execute() {
        def timer = ATimer.start()

        futils.mkdirs(outdir)
        futils.overwrite("$outdir/params.txt", params.toString())

        write "rescoring pockets on proteins from dataset [$dataset.name]"
        log.info "outdir: $outdir"

        Classifier classifier = WekaUtils.loadClassifier(modelf)

        String[] threadPropNames = ["numThreads","numExecutionSlots"]   // names used for num.threads property by different classifiers
        threadPropNames.each { name ->
            if (classifier.hasProperty(name))
                classifier."$name" = 1 // params.threads
        }

        FeatureExtractor extractor = FeatureExtractor.createFactory()

        Dataset.Result result = dataset.processItems(params.parallel, new Dataset.Processor() {
            void processItem(Dataset.Item item) {
                Prediction prediction = item.prediction

                PocketRescorer rescorer = new  WekaSumRescorer(classifier, extractor)
                rescorer.reorderPockets(prediction)

                ReorderingSummary rsumm = new ReorderingSummary(prediction)
                String outf = "$outdir/${item.label}_rescored.csv"
                futils.overwrite(outf, rsumm.toCSV().toString())
                log.info "\n\nRescored pockets for [$item.label]: \n\n" + rsumm.toTable() + "\n"

            }
        })

        write "rescoring finished in $timer.formatted"
        write "results saved to directory [${futils.absPath(outdir)}]"

        return result
    }

}
