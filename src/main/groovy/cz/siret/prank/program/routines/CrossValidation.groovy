package cz.siret.prank.program.routines

import cz.siret.prank.collectors.DataPreProcessor
import cz.siret.prank.domain.Dataset
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.utils.ATimer
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.WekaUtils
import groovy.util.logging.Slf4j
import groovyx.gpars.GParsPool
import weka.core.Instances

import static cz.siret.prank.utils.ATimer.startTimer

@Slf4j
class CrossValidation extends EvalRoutine {

    int numFolds
    int samplingSeed
    Dataset dataset
    List<Fold> folds
    EvalResults results

    int train_positives
    int train_negatives

    CrossValidation(String outdir, Dataset dataset) {
        this.outdir = outdir
        this.dataset = dataset
    }

    void init() {
        results = new EvalResults(0) // joint results
        numFolds = params.folds
        samplingSeed = params.seed
        Futils.mkdirs(outdir)
    }

    @Override
    EvalResults execute() {
        def timer = startTimer()

        init()
        prepareFolds()

        List<EvalResults> resultsList
        GParsPool.withPool(params.crossval_threads) {

            resultsList = folds.collectParallel { Fold fold ->

                TrainEvalRoutine iter = new TrainEvalRoutine()
                iter.label = "fold.${numFolds}.${fold.num}"
                iter.outdir = "$outdir/$iter.label"
                iter.trainDataSet = fold.data.trainset
                iter.evalDataSet = fold.data.evalset
                iter.trainVectors = fold.trainVectors // precollected vectors

                iter.trainAndEvalModel()

                return iter.evalRoutine.results

            }.toList()

        }

        resultsList.each { results.addAll(it) }

        results.train_negatives = train_negatives
        results.train_positives = train_positives

        results.logAndStore(outdir, params.classifier)
        logSummaryResults(dataset.label, "crossvalidation", results)

        write "processed $results.originalEval.ligandCount ligands in $dataset.size files"
        write "crossvalidation finished in $timer.formatted"
        write "results saved to directory [${Futils.absPath(outdir)}]"
        Futils.writeFile("$outdir/time.log", "finished in $timer.formatted")

        return results
    }

    /**
     * samples folds and collects vectors
     */
    private void prepareFolds() {

        folds = dataset.sampleFolds(numFolds, samplingSeed).collect { Dataset.Fold data ->
            Fold fold = new Fold()
            fold.num = data.num
            fold.data = data

            def res = new CollectVectorsRoutine(data.evalset, outdir).collectVectors()
            fold.evalVectors = res.instances
            train_negatives += res.negatives
            train_positives += res.positives

            return fold
        }.toList()

        folds.forEach { Fold fold ->
            List<Fold> otherFolds = folds.minus(fold)
            fold.trainVectors = WekaUtils.joinInstances(otherFolds*.evalVectors)
            fold.trainVectors = new DataPreProcessor().preProcessTrainData(fold.trainVectors)
        }
    }

    static class Fold {
        int num
        Dataset.Fold data
        Instances evalVectors
        Instances trainVectors
    }

}
