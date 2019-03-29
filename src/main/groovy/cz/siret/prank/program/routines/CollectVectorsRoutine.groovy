package cz.siret.prank.program.routines

import cz.siret.prank.collectors.DataPreprocessor
import cz.siret.prank.collectors.VectorCollector
import cz.siret.prank.collectors.CollectorFactory
import cz.siret.prank.domain.Dataset
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.program.PrankException
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.PerfUtils
import cz.siret.prank.utils.WekaUtils
import groovy.transform.TupleConstructor
import groovy.util.logging.Slf4j
import weka.core.Instance
import weka.core.Instances

import java.util.concurrent.atomic.AtomicInteger

import static cz.siret.prank.utils.ATimer.startTimer

@Slf4j
class CollectVectorsRoutine extends Routine {

    Dataset dataset
    String vectf           // arff vector file path

    CollectVectorsRoutine(Dataset dataSet, String outdir) {
        this(dataSet, outdir, "$outdir/vectors.arff")
    }

    CollectVectorsRoutine(Dataset dataSet, String outdir, String vectf) {
        super(outdir)
        this.dataset = dataSet
        this.vectf = vectf
    }

    private Dataset prepareDataset(Dataset dataset) {
        if (params.train_protein_limit>0 && params.train_protein_limit < dataset.size) {
            write "training on random subset of size $params.train_protein_limit"
            dataset = dataset.randomSubset(params.train_protein_limit, params.seed)
        }
        return dataset
    }

    /**
     * collects 
     */
    Result collectVectors() {
        def timer = startTimer()

        write "collecting vectors from dataset [$dataset.name]"

        Futils.mkdirs(outdir)
        writeParams(outdir)

        final FeatureExtractor extractor = FeatureExtractor.createFactory()
        final VectorCollector collector = CollectorFactory.createCollector(extractor, dataset)

        extractor.trainingExtractor = true

        final AtomicInteger pos = new AtomicInteger(0)
        final AtomicInteger neg = new AtomicInteger(0)
        final List<Instances> instList = Collections.synchronizedList(new ArrayList<>(dataset.size))

        dataset = prepareDataset(dataset)

        dataset.processItems { Dataset.Item item ->

            def collected = collector.collectVectors(item.predictionPair, item.context)

            Instances inst = WekaUtils.createDatasetWithBinaryClass(extractor.vectorHeader)
            for (FeatureVector v : collected.vectors) {
                inst.add(WekaUtils.toInstance(v.array))
            }

            pos.addAndGet(collected.positives)
            neg.addAndGet(collected.negatives)
            instList.add(inst)
        }

        int positives = pos.get()
        int negatives = neg.get()
        int count = positives + negatives
        double ratio = PerfUtils.round ( (double)positives / negatives , 3)
        int ligandCount = dataset.items.collect { it.predictionPair.protein.ligands.size() }.sum(0) as int

        write "processed $ligandCount ligans in $dataset.size files"
        write "extracted $count vectors...  positives:$positives negatives:$negatives ratio:$ratio"

        write "preparing instance dataset...."
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

        // TODO move up to TrainEvalRoutine
        data = new DataPreprocessor().preProcessTrainData(data)

        if (!params.delete_vectors) {
            WekaUtils.saveDataArff(arffFile, false, data)
        }

        if (params.check_vectors) {
            for (Instance inst : data) {
                for (int i=0; i!=inst.numAttributes(); i++) {
                    double val = inst.value(i)
                    if (val == Double.NaN) {
                        String feat = inst.attribute(i).name()
                        String msg = "Invalid value for feature $feat: NaN"
                        System.out.println(msg)
                        log.error(msg)
                        throw new PrankException("Invalid value for feature $feat: NaN")
                    }
                }
            }
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
