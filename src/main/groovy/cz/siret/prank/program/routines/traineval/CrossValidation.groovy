package cz.siret.prank.program.routines.traineval

import cz.siret.prank.collectors.DataPreprocessor
import cz.siret.prank.domain.Dataset
import cz.siret.prank.program.ml.FeatureVectors
import cz.siret.prank.program.routines.results.EvalResults
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.WekaUtils
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Slf4j
import groovyx.gpars.GParsPool
import weka.core.Instances

import static cz.siret.prank.utils.ATimer.startTimer

@Slf4j
@CompileStatic
class CrossValidation extends EvalRoutine {

    int numFolds
    int samplingSeed
    Dataset dataset
    List<Fold> folds
    EvalResults results

    int train_positives
    int train_negatives

    CrossValidation(String outdir, Dataset dataset) {
        super(outdir)
        this.dataset = Objects.requireNonNull(dataset, "Dataset for cross-validation was not provided.")
    }

    void init() {
        results = new EvalResults(0) // joint results
        numFolds = params.folds
        samplingSeed = params.seed
        Futils.mkdirs(outdir)
    }


    /**
     * TODO make crossvalidation work with hopt_train_only_once
     */
    @CompileStatic(TypeCheckingMode.SKIP)
    @Override
    EvalResults execute() {
        def timer = startTimer()

        init()
        prepareFolds()

        List<EvalResults> resultsList
        GParsPool.withPool(params.crossval_threads) {
            resultsList = folds.collectParallel { Fold fold ->

                String label = "fold.${numFolds}.${fold.num}"
                TrainEvalRoutine iter = new TrainEvalRoutine("$outdir/$label", fold.data.trainset, fold.data.evalset)
                iter.trainVectors = FeatureVectors.fromInstances(fold.trainVectors) // pre-collected vectors

                return iter.trainAndEvalModel()
            } as List<EvalResults>
        }

        resultsList.each { results.addSubResults(it) }

        results.train_negatives = train_negatives
        results.train_positives = train_positives

        results.logAndStore(outdir, params.classifier)
        logSummaryResults(dataset.label, "crossvalidation", results)

        write "processed $results.origEval.ligandCount ligands in $dataset.size files"
        write "crossvalidation finished in $timer.formatted"
        write "results saved to directory [${Futils.absPath(outdir)}]"
        logTime "finished in $timer.formatted"

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
            fold.trainVectors = new DataPreprocessor().preProcessTrainData(fold.trainVectors)
        }
    }

    static class Fold {
        int num
        Dataset.Fold data
        Instances evalVectors
        Instances trainVectors
    }

}
