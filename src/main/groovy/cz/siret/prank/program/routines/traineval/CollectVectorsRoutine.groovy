package cz.siret.prank.program.routines.traineval


import cz.siret.prank.collectors.CollectorFactory
import cz.siret.prank.collectors.DataPreprocessor
import cz.siret.prank.collectors.VectorCollector
import cz.siret.prank.domain.Dataset
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.ml.FeatureVectors
import cz.siret.prank.program.routines.Routine
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.PerfUtils
import cz.siret.prank.utils.WekaUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import weka.core.Instance
import weka.core.Instances

import java.util.concurrent.atomic.AtomicInteger

import static cz.siret.prank.utils.ATimer.startTimer
import static cz.siret.prank.utils.Cutils.newSynchronizedList

@Slf4j
@CompileStatic
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
    FeatureVectors collectVectors() {
        def timer = startTimer()

        write "collecting vectors from dataset [$dataset.name]"

        Futils.mkdirs(outdir)
        writeParams(outdir)

        final FeatureExtractor extractor = FeatureExtractor.createFactory()
        final VectorCollector collector = CollectorFactory.createCollector(extractor, dataset)

        extractor.forTraining = true

        final AtomicInteger pos = new AtomicInteger(0)
        final AtomicInteger neg = new AtomicInteger(0)
        final AtomicInteger ligCount = new AtomicInteger(0)
        final List<Instances> instList = newSynchronizedList(dataset.size)

        dataset = prepareDataset(dataset)

        if (dataset.size == 0) {
            throw new PrankException("Datsets has no items [$dataset.name].")
        }

        dataset.processItems { Dataset.Item item ->

            def collected = collector.collectVectors(item.predictionPair, item.context)

            Instances inst = WekaUtils.createDatasetWithBinaryClass(extractor.vectorHeader)
            for (FeatureVector v : collected.vectors) {
                inst.add(WekaUtils.toInstance(v.array))
            }

            pos.addAndGet(collected.positives)
            neg.addAndGet(collected.negatives)
            ligCount.addAndGet(item.predictionPair.ligands.relevantLigandCount)
            instList.add(inst)
        }

        int positives = pos.get()
        int negatives = neg.get()
        int count = positives + negatives
        double ratio = PerfUtils.round ( (double)positives / negatives , 4)
        int ligandCount = ligCount.get()

        write "processed $ligandCount ligands in $dataset.size files"
        write "extracted $count vectors...  positives:$positives negatives:$negatives ratio:$ratio"

        write "preparing instance dataset...."

        if (instList.size() == 0) {
            throw new PrankException("Vectors from no protein were collected for dataset [$dataset.name].")
        }
        if (count == 0) {
            throw new PrankException("No vectors were extracted from dataset [$dataset.name].")
        }
        if (positives == 0) {
            throw new PrankException("No positive vectors were extracted from dataset [$dataset.name].")
        }

        Instances data = prepareDataForWeka(instList, vectf)

        logTime "collecting vectors finished in $timer.formatted"

        return FeatureVectors.fromInstances(data)
    }


    Instances prepareDataForWeka(List<Instances> instList, String arffFile) {
        Instances data = WekaUtils.joinInstances(instList)

        log.info "instances: " + data.size()

        // TODO move up to TrainEvalRoutine
        data = new DataPreprocessor().preProcessTrainData(data)

        if (!params.delete_vectors) {
            WekaUtils.saveDataArff(Futils.getGzipOutputStream(arffFile+".gz"), data)
        }

        if (params.check_vectors) {
            for (Instance inst : data) {
                for (int i=0; i!=inst.numAttributes(); i++) {
                    double val = inst.value(i)
                    if (Double.isNaN(val)) {
                        String feat = inst.attribute(i).name()
                        throw new PrankException("Invalid value for feature '$feat': NaN")
                    }
                }
            }
        }

        return data
    }

}
