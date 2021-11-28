package cz.siret.prank.utils


import cz.siret.prank.program.PrankException
import cz.siret.prank.program.ml.FeatureVectors
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import weka.classifiers.Classifier
import weka.core.*
import weka.core.converters.ArffSaver
import weka.core.converters.ConverterUtils
import weka.filters.Filter
import weka.filters.unsupervised.attribute.NumericToNominal
import weka.filters.unsupervised.instance.Randomize

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Slf4j
@CompileStatic
class WekaUtils {

    private static final int BUFFER_SIZE = 16 * 1024 * 1024;

    static Instances createDatasetWithBinaryClass(List<String> header, int initialCapacity = 10) {

        ArrayList<Attribute> attInfo = new ArrayList<>()
        int i = 0
        attInfo.addAll(header.collect {
            new Attribute(it, i++)
        })

        attInfo.add(new Attribute("@@class@@", ["0", "1"], new ProtectedProperties(new Properties())))

        Instances auxWekaDataset = new Instances("TestInstances", attInfo, initialCapacity);
        auxWekaDataset.setClassIndex(auxWekaDataset.numAttributes() - 1)

        //log.debug "NUM CLASSES: " + auxWekaDataset.numClasses()

        return auxWekaDataset
    }

    static Instance toInstance(double[] vector) {
        return new DenseInstance(1, vector)
    }

    // == classifiers ===

    static void saveClassifier(Classifier classifier, String fileName) {

        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(fileName), BUFFER_SIZE))
        //zos.setLevel(9)

        ZipEntry entry = new ZipEntry("weka.model")
        zos.putNextEntry(entry)

        def oos = new ObjectOutputStream(new BufferedOutputStream(zos, BUFFER_SIZE))
        oos.writeObject(classifier)
        oos.flush()

        //SerializationHelper.write(zos, classifier)

        zos.closeEntry()

        oos.close()
    }

    static Classifier loadClassifier(String fileName) {
        InputStream zis = null
        try {
            zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(fileName), BUFFER_SIZE))
            zis.getNextEntry()

            return loadClassifier(zis)
        } catch (Exception e) {
            throw new PrankException("Failed to load model from file '$fileName'", e)
        } finally {
            zis?.close()
        }
    }

    static Classifier loadClassifier(InputStream ins) {
        try {
            return (Classifier) SerializationHelper.read(ins)
        } finally {
            ins.close()
        }
    }

    /**
     * tries to make sure that classifier uses only one thread for each classification (we then parallelize dataset)
     * @param classifier
     */
    @CompileDynamic
    static void disableParallelism(Classifier classifier) {
        String[] threadPropNames = ["numThreads","numExecutionSlots"]   // names used for num.threads property by different classifiers
        threadPropNames.each { String name ->
            if (classifier.hasProperty(name))
                classifier."$name" = 1 // params.threads
        }
    }

    /**
     * load from jar
     */
    static Classifier loadClassifierFromPath(String path) {
        return (Classifier) SerializationHelper.read(path.class.getResourceAsStream(path));
    }

    static void trainClassifier(Classifier classifier, FeatureVectors data) {
        validateDataset(data.instances)
        classifier.buildClassifier(data.instances)
    }

    static void validateDataset(Instances data) {
        DatasetStats stats = stats(data)
        if (stats.positives == 0) {
            throw new PrankException("There are no positive instances in the training dataset! " + stats)
        }
        if (stats.negatives == 0) {
            throw new PrankException("There are no negative instances in the training dataset! " + stats)
        }
    }

    // == data ===

    static Instances loadData(String fileName) {
        ConverterUtils.DataSource source = new ConverterUtils.DataSource(Futils.inputStream(fileName))
        Instances data = source.getDataSet();

        // setting class attribute if the data format does not provide this information
        if (data.classIndex() == -1)
            data.setClassIndex(data.numAttributes() - 1);

        return data
    }

    static void saveDataArff(OutputStream outs, Instances data) {
        ArffSaver saver = new ArffSaver()
        saver.setCompressOutput(false)
        saver.setDestination(outs)
        saver.setInstances(data)
        saver.writeBatch() // closes outs
    }

    static Instances joinInstances(List<Instances> instList) {
        assert !instList.empty

        Instances res = new Instances(instList.head())

        for (Instances inst : instList.tail()) {
            res.addAll(inst)
        }

        return res
    }

    static List<Instances> splitPositivesNegatives(Instances all) {
        Instances pos = new Instances(all, 0)
        Instances neg = new Instances(all, 0)

        for (Instance inst : all) {
            if (isPositiveInstance(inst)) {
                pos.add(inst)
            } else {
                neg.add(inst)  // note: this is copying the instance!
            }
        }

        return [pos, neg]
    }

    static isPositiveInstance(Instance inst) {
        inst.classValue() != 0    
    }

    static int countClass(Instances data, double val) {
        int sum = 0
        for (Instance inst : data) {
            if (inst.classValue() == val) {
                sum++
            }
        }
        sum
    }

    static int countPositives(Instances data) {
        countClass(data, 1d)
    }

    static int countNegatives(Instances data) {
        countClass(data, 0d)
    }

    // == filters ===

    static Instances randomSubsample(double keepRatio, int seed, Instances data) {
        assert keepRatio>=0 && keepRatio<=1, "Invalid ratio for subsample: $keepRatio"

        return removeRatio( randomize(data, seed), 1-keepRatio)
    }

    /**
     * subsample or supersample by copying data n times and adding random subsample
     */
    static Instances randomSample(double ratio, int seed, Instances data) {
        assert ratio>=0, "Invalid ratio for sampling: $ratio"

        if (ratio < 1) {
            return randomSubsample(ratio, seed, data)
        } else {
            Instances result = new Instances(data, 0, data.size())
            ratio -= 1
            while (ratio>=1) {
                result.addAll(data)
                ratio -= 1
            }
            result.addAll( randomSubsample(ratio, seed, data) )
            return result
        }
    }

    /**
     *
     * @param removeRatio
     * @param data
     * @return
     */
    static Instances removeRatio(Instances data, double removeRatio) {
        int keepCount = (int)Math.round(data.size() * (1d-removeRatio))
        Instances result = new Instances(data, 0, keepCount)
        return result
    }

    static Instances randomize(Instances data, int seed) {
        Randomize filter = new Randomize()
        filter.setInputFormat(data)
        filter.setRandomSeed(seed)
        return Filter.useFilter(data, filter)
    }

    static Instances reverse(Instances data) {
        Instances res = new Instances(data, data.size())
        res.addAll(data.reverse())
        return res
    }

    static Instances numericToNominal(String attributeIndices, Instances data) {

        NumericToNominal filter = new NumericToNominal()
        filter.setInputFormat(data)
        filter.setAttributeIndices(attributeIndices)
        return Filter.useFilter(data, filter)
    }

    static DatasetStats stats(Instances data) {
        return new DatasetStats(countPositives(data), countNegatives(data))
    }

    //===========================================================================================================//

    static class DatasetStats {
        int count
        int positives
        int negatives

        DatasetStats(int positives, int negatives) {
            this.positives = positives
            this.negatives = negatives
            this.count = positives + negatives
        }

        @Override
        String toString() {
            return "(count:$count, positives:$positives, negatives:$negatives)"
        }

    }
    
}
