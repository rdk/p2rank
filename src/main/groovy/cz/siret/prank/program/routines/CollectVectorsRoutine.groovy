package cz.siret.prank.program.routines

import cz.siret.prank.utils.MathUtils
import cz.siret.prank.utils.PerfUtils
import groovy.transform.TupleConstructor
import groovy.util.logging.Slf4j
import cz.siret.prank.collectors.DataPreProcessor
import cz.siret.prank.collectors.PointVectorCollector
import cz.siret.prank.collectors.VectorCollector
import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.score.criteria.DCA
import cz.siret.prank.score.criteria.IdentificationCriterium
import cz.siret.prank.utils.ATimer
import cz.siret.prank.utils.WekaUtils
import cz.siret.prank.utils.futils
import weka.core.Instances

import java.util.concurrent.atomic.AtomicInteger

@Slf4j
class CollectVectorsRoutine extends Routine {

    Dataset dataset
    String vectf           // arff vector file path

    double IDENTIFIED_POCKET_CUTOFF = 5

    CollectVectorsRoutine(Dataset dataSet, String outdir) {
        this(dataSet, outdir, "$outdir/vectors.arff")
    }

    CollectVectorsRoutine(Dataset dataSet, String outdir, String vectf) {
        this.dataset = dataSet
        this.outdir = outdir
        this.vectf = vectf
    }

    /**
     * collects 
     */
    Result collectVectors() {
        def timer = ATimer.start()

        write "collecting vectors from dataset [$dataset.name]"

        futils.mkdirs(outdir)
        futils.overwrite("$outdir/params.txt", params.toString())

//===========================================================================================================//

        final IdentificationCriterium identifiedPocketAssessor = new DCA(IDENTIFIED_POCKET_CUTOFF)
        final FeatureExtractor extractor = FeatureExtractor.createFactory()
        extractor.trainingExtractor = true

        final AtomicInteger pos = new AtomicInteger(0)
        final AtomicInteger neg = new AtomicInteger(0)
        final List<Instances> instList = Collections.synchronizedList( new ArrayList<>(dataset.size))


        dataset.processItems(params.parallel, new Dataset.Processor() {
            void processItem(Dataset.Item item) {
                PredictionPair pair = item.predictionPair

                final VectorCollector collector = new PointVectorCollector(extractor, identifiedPocketAssessor)
                final VectorCollector.Result res = collector.collectVectors(pair)

                Instances inst = WekaUtils.createDatasetWithBinaryClass(extractor.vectorHeader)
                for (FeatureVector v : res.vectors) {
                    inst.add(WekaUtils.toInstance(v.vector))
                }

                pos.addAndGet(res.positives)
                neg.addAndGet(res.negatives)
                instList.add(inst)

            }
        });

        int positives = pos.get()
        int negatives = neg.get()
        int count = positives + negatives
        double ratio = PerfUtils.round ( (double)positives / negatives , 3)
        int ligandCount = dataset.items.collect { it.predictionPair.liganatedProtein.ligands.size() }.sum()


        write "processed $ligandCount ligans in $dataset.size files"
        write "extracted $count vectors...  positives:$positives negatives:$negatives ratio:$ratio"

        write "preparing data for weka...."
        Instances data = prepareDataForWeka(instList, vectf)
        positives = WekaUtils.countPositives(data)
        negatives = WekaUtils.countNegatives(data)
        count = positives + negatives


        logTime "collecting vectors finished in $timer.formatted"

        return new Result(instances: data, count: count, positives: positives, negatives: negatives)
    }


    Instances prepareDataForWeka(List<Instances> instList, String arffFile) {

        Instances data = WekaUtils.joinInstances(instList)

        log.info "instances: " + data.size()
        //data = WekaUtils.numericToNominal("last", data)

        // TODO move up to TrainEvalIteration
        data = new DataPreProcessor().preProcessTrainData(data)

        if (!params.delete_vectors) {
            WekaUtils.saveDataArff(arffFile, false, data)
        }

        return data
    }

    @TupleConstructor
    static class Result {
        Instances instances
        int count
        int positives
        int negatives
    }

}
