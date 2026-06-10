package cz.siret.prank.program.ml

import cz.siret.prank.fforest.FasterForest
import cz.siret.prank.fforest.api.BinaryForest
import cz.siret.prank.fforest.api.FasterForestConverter
import cz.siret.prank.fforest.api.FasterForestConverter.ForestType
import cz.siret.prank.fforest.api.FlatBinaryForest
import cz.siret.prank.fforest.api.FlatBinaryForestBuilder
import cz.siret.prank.fforest.api.LegacyFlatBinaryForest
import cz.siret.prank.fforest.api.SoaLegacyFlatBinaryForest
import cz.siret.prank.fforest.api.Int16LeafSoaLegacyFlatBinaryForest
import cz.siret.prank.fforest.api.NativePanamaForest
import cz.siret.prank.fforest.api.TrainableFasterForest
import cz.siret.prank.fforest.api.WekaRandomForestConverter
import cz.siret.prank.fforest2.FasterForest2
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.WekaUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import hr.irb.fastRandomForest.FastRandomForest
import weka.classifiers.trees.RandomForest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock
import weka.core.DenseInstance
import weka.core.Instances

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests for ModelConverter — training small forests and converting between all supported forest types.
 */
@Isolated
@ResourceLock("Params")
@CompileStatic
@Slf4j
class ModelConverterTest {

    static final int NUM_FEATURES = 10
    static final int NUM_TREES = 4
    static final int MAX_DEPTH = 6
    static final int NUM_INSTANCES = 200

    @BeforeAll
    static void initAll() {
        Params.INSTANCE = new Params()
    }

    @AfterAll
    static void tearDownAll() {
        Params.INSTANCE = new Params()
    }

//===========================================================================================================//

    /**
     * Create a simple synthetic binary classification dataset.
     */
    private static FeatureVectors createSyntheticData() {
        List<String> header = (0..<NUM_FEATURES).collect { "f${it}" as String }
        Instances instances = WekaUtils.createDatasetWithBinaryClass(header, NUM_INSTANCES)

        Random rng = new Random(42)
        for (int i = 0; i < NUM_INSTANCES; i++) {
            double[] values = new double[NUM_FEATURES + 1]
            for (int f = 0; f < NUM_FEATURES; f++) {
                values[f] = rng.nextGaussian()
            }
            // simple rule: positive if sum of first 3 features > 0
            double classVal = (values[0] + values[1] + values[2] > 0) ? 1.0d : 0.0d
            values[NUM_FEATURES] = classVal
            instances.add(new DenseInstance(1.0, values))
        }

        int positives = WekaUtils.countPositives(instances)
        int negatives = WekaUtils.countNegatives(instances)
        return new FeatureVectors(instances, positives, negatives)
    }

    /**
     * Train a classifier by name and return it as a Model.
     */
    private static Model trainSmallModel(String classifierName) {
        Params params = new Params()
        params.rf_trees = NUM_TREES
        params.rf_depth = MAX_DEPTH
        params.rf_features = 0
        params.rf_bagsize = 100
        params.seed = 42
        params.threads = 1
        params.feature_importances = false

        Model model = new Model(classifierName, new ClassifierFactory(params).createClassifier(classifierName))
        FeatureVectors data = createSyntheticData()
        WekaUtils.trainClassifier(model.asWekaClassifier(), data)
        return model
    }

    /**
     * Create test instances for prediction.
     */
    private static double[][] createTestVectors(int count) {
        Random rng = new Random(123)
        double[][] vectors = new double[count][]
        for (int i = 0; i < count; i++) {
            vectors[i] = new double[NUM_FEATURES]
            for (int f = 0; f < NUM_FEATURES; f++) {
                vectors[i][f] = rng.nextGaussian()
            }
        }
        return vectors
    }

    /**
     * Assert that predictions from two forests match within tolerance.
     */
    private static void assertPredictionsMatch(BinaryForest a, BinaryForest b, double[][] testVectors, double tolerance) {
        for (int i = 0; i < testVectors.length; i++) {
            double predA = a.predict(testVectors[i])
            double predB = b.predict(testVectors[i])
            assertEquals(predA, predB, tolerance, "Prediction mismatch at instance $i")
        }

        // also test batch prediction
        double[] batchA = a.predictForBatch(testVectors)
        double[] batchB = b.predictForBatch(testVectors)
        assertEquals(batchA.length, batchB.length)
        for (int i = 0; i < batchA.length; i++) {
            assertEquals(batchA[i], batchB[i], tolerance, "Batch prediction mismatch at instance $i")
        }
    }

    /**
     * Fraction of point-pairs ordered the same way by two score vectors (Kendall concordance, ties skipped).
     * 1.0 == identical ranking. Used to validate ranking-equivalence of an approximate variant.
     */
    private static double concordance(double[] a, double[] b) {
        int n = a.length
        long conc = 0, disc = 0
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double da = a[i] - a[j], db = b[i] - b[j]
                if (da == 0.0d || db == 0.0d) continue
                if ((da > 0.0d) == (db > 0.0d)) conc++ else disc++
            }
        }
        return (conc + disc) == 0L ? 1.0d : conc / ((double) (conc + disc))
    }

//===========================================================================================================//

    @Test
    void testConvertFasterForestToAllTypes() {
        Model model = trainSmallModel("FasterForest")
        assert model.classifier instanceof FasterForest

        TrainableFasterForest trainable = (TrainableFasterForest) model.classifier
        double[][] testVectors = createTestVectors(50)

        for (ForestType type : ForestType.values()) {
            if (type == ForestType.NativePanamaForest && !NativePanamaForest.isAvailable()) continue
            if (type == ForestType.NativePanamaFloatForest && !NativePanamaForest.isAvailable()) continue
            if (type == ForestType.NativePanamaForestAvx2) continue  // skip AVX2
            if (type == ForestType.NativePanamaFloatForestAvx2) continue  // skip AVX2
            if (type == ForestType.PureLeafLegacyFlatBinaryForest) continue  // requires pure leaves; the depth-limited synthetic model is impure (throws by design)

            BinaryForest converted = FasterForestConverter.convertFasterForest(trainable, type)
            assertNotNull(converted, "Conversion to $type returned null")
            assertEquals(NUM_TREES, converted.numTrees, "numTrees mismatch for $type")
            assertEquals(NUM_FEATURES + 1, converted.numAttributes, "numAttributes mismatch for $type")
            // verify predictions are valid probabilities
            for (double[] vec : testVectors) {
                double pred = converted.predict(vec)
                assertTrue(pred >= 0.0d && pred <= 1.0d, "Prediction out of range for $type: $pred")
            }

            log.info "Converted to ${type}: ${converted.class.simpleName}"
        }
    }

    @Test
    void testConvertFasterForest2ToAllTypes() {
        Model model = trainSmallModel("FasterForest2")
        assert model.classifier instanceof FasterForest2

        TrainableFasterForest trainable = (TrainableFasterForest) model.classifier
        double[][] testVectors = createTestVectors(50)

        for (ForestType type : ForestType.values()) {
            if (type == ForestType.NativePanamaForest && !NativePanamaForest.isAvailable()) continue
            if (type == ForestType.NativePanamaFloatForest && !NativePanamaForest.isAvailable()) continue
            if (type == ForestType.NativePanamaForestAvx2) continue
            if (type == ForestType.NativePanamaFloatForestAvx2) continue
            if (type == ForestType.PureLeafLegacyFlatBinaryForest) continue  // requires pure leaves; the depth-limited synthetic model is impure (throws by design)

            BinaryForest converted = FasterForestConverter.convertFasterForest(trainable, type)
            assertNotNull(converted, "Conversion to $type returned null")
            assertEquals(NUM_TREES, converted.numTrees)

            for (double[] vec : testVectors) {
                double pred = converted.predict(vec)
                assertTrue(pred >= 0.0d && pred <= 1.0d, "Prediction out of range for $type: $pred")
            }
        }
    }

    @Test
    void testConvertFastRandomForest() {
        Model model = trainSmallModel("FastRandomForest")
        assert model.classifier instanceof FastRandomForest

        ModelConverter converter = new ModelConverter()
        TrainableFasterForest trainable = converter.frfToTrainableBinaryForest((FastRandomForest) model.classifier)

        assertNotNull(trainable)
        assertEquals(NUM_TREES, trainable.trees.size())
        assertEquals(NUM_FEATURES + 1, trainable.numAttributes)

        double[][] testVectors = createTestVectors(50)

        // convert to several types
        for (ForestType type : [ForestType.FlatBinaryForest, ForestType.LegacyFlatBinaryForest, ForestType.InterleavedBfsForest]) {
            BinaryForest converted = FasterForestConverter.convertFasterForest(trainable, type)
            assertNotNull(converted)
            assertEquals(NUM_TREES, converted.numTrees)

            for (double[] vec : testVectors) {
                double pred = converted.predict(vec)
                assertTrue(pred >= 0.0d && pred <= 1.0d, "Prediction out of range for $type: $pred")
            }
        }
    }

    @Test
    void testConvertRandomForest() {
        Model model = trainSmallModel("RandomForest")
        assert model.classifier instanceof RandomForest

        TrainableFasterForest trainable = WekaRandomForestConverter.toFasterTreeForest((RandomForest) model.classifier)

        assertNotNull(trainable)
        assertEquals(NUM_TREES, trainable.trees.size())
        assertEquals(NUM_FEATURES + 1, trainable.numAttributes)

        double[][] testVectors = createTestVectors(50)

        // convert to several types
        for (ForestType type : [ForestType.FlatBinaryForest, ForestType.LegacyFlatBinaryForest, ForestType.InterleavedBfsForest]) {
            BinaryForest converted = FasterForestConverter.convertFasterForest(trainable, type)
            assertNotNull(converted)
            assertEquals(NUM_TREES, converted.numTrees)

            for (double[] vec : testVectors) {
                double pred = converted.predict(vec)
                assertTrue(pred >= 0.0d && pred <= 1.0d, "Prediction out of range for $type: $pred")
            }
        }
    }

//===========================================================================================================//

    @Test
    void testFlattenViaModelConverter() {
        Params originalParams = (Params) Params.inst.clone()
        try {
            Params.inst.rf_flatten = true
            Params.inst.rf_flatten_target = "FlatBinaryForest"
            Params.inst.threads = 1

            Model model = trainSmallModel("FasterForest")
            Model flattened = new ModelConverter().applyConversions(model)

            assertTrue(flattened.classifier instanceof FlatBinaryForest)
            assertTrue(flattened.label.contains("FlatBinaryForest"))

            BinaryForest forest = (BinaryForest) flattened.classifier
            assertEquals(NUM_TREES, forest.numTrees)

            // verify predictions work
            double[][] testVectors = createTestVectors(20)
            double[] batch = forest.predictForBatch(testVectors)
            assertEquals(testVectors.length, batch.length)
            for (double p : batch) {
                assertTrue(p >= 0.0d && p <= 1.0d)
            }
        } finally {
            Params.INSTANCE = originalParams
        }
    }

    @Test
    void testFlattenRandomForestViaModelConverter() {
        Params originalParams = (Params) Params.inst.clone()
        try {
            Params.inst.rf_flatten = true
            Params.inst.rf_flatten_target = "FlatBinaryForest"
            Params.inst.threads = 1

            Model model = trainSmallModel("RandomForest")
            Model flattened = new ModelConverter().applyConversions(model)

            assertTrue(flattened.classifier instanceof FlatBinaryForest)
            assertTrue(flattened.label.contains("FlatBinaryForest"))

            BinaryForest forest = (BinaryForest) flattened.classifier
            assertEquals(NUM_TREES, forest.numTrees)

            // verify predictions work
            double[][] testVectors = createTestVectors(20)
            double[] batch = forest.predictForBatch(testVectors)
            assertEquals(testVectors.length, batch.length)
            for (double p : batch) {
                assertTrue(p >= 0.0d && p <= 1.0d)
            }
        } finally {
            Params.INSTANCE = originalParams
        }
    }

    @Test
    void testFlattenToLegacyFlatBinaryForest() {
        Params originalParams = (Params) Params.inst.clone()
        try {
            Params.inst.rf_flatten = true
            Params.inst.rf_flatten_target = "LegacyFlatBinaryForest"
            Params.inst.threads = 1

            Model model = trainSmallModel("FasterForest")
            Model flattened = new ModelConverter().applyConversions(model)

            assertTrue(flattened.classifier instanceof LegacyFlatBinaryForest)
            assertTrue(flattened.label.contains("LegacyFlatBinaryForest"))
        } finally {
            Params.INSTANCE = originalParams
        }
    }

    @Test
    void testFlattenToSoaLegacyFlatBinaryForest() {
        // SoaLegacy is FAITHFUL and bit-exact to LegacyFlat — flattening to it must not change any prediction.
        Params originalParams = (Params) Params.inst.clone()
        try {
            Params.inst.rf_flatten = true
            Params.inst.rf_flatten_target = "SoaLegacyFlatBinaryForest"
            Params.inst.threads = 1

            Model model = trainSmallModel("FasterForest")
            TrainableFasterForest trainable = (TrainableFasterForest) model.classifier
            BinaryForest legacy = FasterForestConverter.convertFasterForest(trainable, ForestType.LegacyFlatBinaryForest)

            Model flattened = new ModelConverter().applyConversions(model)
            assertTrue(flattened.classifier instanceof SoaLegacyFlatBinaryForest)
            assertTrue(flattened.label.contains("SoaLegacyFlatBinaryForest"))

            // Faithful contract: bit-identical to LegacyFlat.
            double[][] testVectors = createTestVectors(100)
            assertPredictionsMatch(legacy, (BinaryForest) flattened.classifier, testVectors, 0.0d)
        } finally {
            Params.INSTANCE = originalParams
        }
    }

    @Test
    void testFlattenToInt16LeafSoaLegacyFlatBinaryForest() {
        // Int16LeafSoa is FAITHFUL-family but APPROXIMATE (int16-quantized leaves): not bit-exact, but must
        // rank instances equivalently to LegacyFlat and stay numerically very close.
        Params originalParams = (Params) Params.inst.clone()
        try {
            Params.inst.rf_flatten = true
            Params.inst.rf_flatten_target = "Int16LeafSoaLegacyFlatBinaryForest"
            Params.inst.threads = 1

            Model model = trainSmallModel("FasterForest")
            TrainableFasterForest trainable = (TrainableFasterForest) model.classifier
            BinaryForest legacy = FasterForestConverter.convertFasterForest(trainable, ForestType.LegacyFlatBinaryForest)

            Model flattened = new ModelConverter().applyConversions(model)
            assertTrue(flattened.classifier instanceof Int16LeafSoaLegacyFlatBinaryForest)
            assertTrue(flattened.label.contains("Int16LeafSoaLegacyFlatBinaryForest"))

            // Ranking-equivalence (not value-equivalence): identical ordering + tiny value drift.
            double[][] testVectors = createTestVectors(200)
            double[] refScores = legacy.predictForBatch(testVectors)
            double[] candScores = ((BinaryForest) flattened.classifier).predictForBatch(testVectors)
            double conc = concordance(refScores, candScores)
            assertTrue(conc >= 0.999d, "Int16LeafSoa ranking concordance with LegacyFlat too low: $conc")
            assertPredictionsMatch(legacy, (BinaryForest) flattened.classifier, testVectors, 1e-2d)
        } finally {
            Params.INSTANCE = originalParams
        }
    }

//===========================================================================================================//

    @Test
    void testRoundTripFlatBinaryForest() {
        Model model = trainSmallModel("FasterForest")
        TrainableFasterForest original = (TrainableFasterForest) model.classifier
        double[][] testVectors = createTestVectors(50)

        // FasterForest -> FlatBinaryForest
        BinaryForest flat = FasterForestConverter.convertFasterForest(original, ForestType.FlatBinaryForest)
        // FlatBinaryForest -> FasterTreeForest (TrainableFasterForest)
        TrainableFasterForest roundTripped = FlatBinaryForestBuilder.toFasterTreeForest((FlatBinaryForest) flat)
        // FasterTreeForest -> FlatBinaryForest again
        BinaryForest flat2 = FasterForestConverter.convertFasterForest(roundTripped, ForestType.FlatBinaryForest)

        assertPredictionsMatch(flat, flat2, testVectors, 1e-9)
    }

    @Test
    void testRoundTripLegacyFlatBinaryForest() {
        Model model = trainSmallModel("FasterForest")
        TrainableFasterForest original = (TrainableFasterForest) model.classifier
        double[][] testVectors = createTestVectors(50)

        // FasterForest -> LegacyFlatBinaryForest
        BinaryForest legacy = FasterForestConverter.convertFasterForest(original, ForestType.LegacyFlatBinaryForest)
        // LegacyFlatBinaryForest -> FasterTreeForest
        TrainableFasterForest roundTripped = FlatBinaryForestBuilder.toFasterTreeForest((LegacyFlatBinaryForest) legacy)
        // FasterTreeForest -> LegacyFlatBinaryForest again
        BinaryForest legacy2 = FasterForestConverter.convertFasterForest(roundTripped, ForestType.LegacyFlatBinaryForest)

        assertPredictionsMatch(legacy, legacy2, testVectors, 1e-9)
    }

//===========================================================================================================//

    @Test
    void testReflattenFlatBinaryForestModel() {
        // Test: flatten a model that is already a FlatBinaryForest to a different type
        Params originalParams = (Params) Params.inst.clone()
        try {
            Params.inst.rf_flatten = true
            Params.inst.threads = 1

            // First flatten to FlatBinaryForest
            Params.inst.rf_flatten_target = "FlatBinaryForest"
            Model model = trainSmallModel("FasterForest")
            Model flat = new ModelConverter().applyConversions(model)
            assertTrue(flat.classifier instanceof FlatBinaryForest)

            // Now re-flatten to LegacyFlatBinaryForest
            Params.inst.rf_flatten_target = "LegacyFlatBinaryForest"
            Model reflatModel = new ModelConverter().flattenRandomForest(flat, "LegacyFlatBinaryForest")
            assertTrue(reflatModel.classifier instanceof LegacyFlatBinaryForest)

            BinaryForest forest = (BinaryForest) reflatModel.classifier
            assertEquals(NUM_TREES, forest.numTrees)

            // verify predictions still work
            double[][] testVectors = createTestVectors(20)
            for (double[] vec : testVectors) {
                double pred = forest.predict(vec)
                assertTrue(pred >= 0.0d && pred <= 1.0d)
            }
        } finally {
            Params.INSTANCE = originalParams
        }
    }

    @Test
    void testReflattenLegacyToFlatBinaryForest() {
        // LegacyFlatBinaryForest -> FlatBinaryForest (lossless path)
        Params originalParams = (Params) Params.inst.clone()
        try {
            Params.inst.rf_flatten = true
            Params.inst.threads = 1

            Params.inst.rf_flatten_target = "LegacyFlatBinaryForest"
            Model model = trainSmallModel("FasterForest")
            Model legacy = new ModelConverter().applyConversions(model)
            assertTrue(legacy.classifier instanceof LegacyFlatBinaryForest)

            Model reflatModel = new ModelConverter().flattenRandomForest(legacy, "InterleavedBfsForest")
            assertTrue(reflatModel.classifier instanceof BinaryForest)

            BinaryForest forest = (BinaryForest) reflatModel.classifier
            assertEquals(NUM_TREES, forest.numTrees)
        } finally {
            Params.INSTANCE = originalParams
        }
    }

//===========================================================================================================//

    @Test
    void testNativePanamaForest() {
        if (!NativePanamaForest.isAvailable()) {
            log.info "NativePanamaForest not available on this platform, skipping"
            return
        }

        Model model = trainSmallModel("FasterForest")
        TrainableFasterForest trainable = (TrainableFasterForest) model.classifier
        double[][] testVectors = createTestVectors(50)

        // convert to reference format
        BinaryForest reference = FasterForestConverter.convertFasterForest(trainable, ForestType.FlatBinaryForest)
        // convert to NativePanama
        BinaryForest nativeForest = FasterForestConverter.convertFasterForest(trainable, ForestType.NativePanamaForest)

        assertNotNull(nativeForest)
        assertEquals(NUM_TREES, nativeForest.numTrees)

        // NativePanama predictions should match reference (within float precision)
        assertPredictionsMatch(reference, nativeForest, testVectors, 1e-5)

        // test batch prediction
        double[] batchResults = nativeForest.predictForBatch(testVectors)
        assertEquals(testVectors.length, batchResults.length)
    }

//===========================================================================================================//

    @Test
    void testIsFlattableClassifier() {
        assertTrue(ModelConverter.isFlattableClassifier(new FasterForest()))
        assertTrue(ModelConverter.isFlattableClassifier(new FasterForest2()))
        assertTrue(ModelConverter.isFlattableClassifier(new FastRandomForest()))
        assertTrue(ModelConverter.isFlattableClassifier(new RandomForest()))

        assertFalse(ModelConverter.isFlattableClassifier("not a classifier"))
        assertFalse(ModelConverter.isFlattableClassifier(42))
    }

    @Test
    void testInvalidFlattenTarget() {
        Params originalParams = (Params) Params.inst.clone()
        try {
            Params.inst.rf_flatten = true
            Params.inst.rf_flatten_target = "NonExistentForestType"
            Params.inst.threads = 1

            Model model = trainSmallModel("FasterForest")
            assertThrows(IllegalArgumentException) {
                new ModelConverter().applyConversions(model)
            }
        } finally {
            Params.INSTANCE = originalParams
        }
    }

    @Test
    void testEmptyFlattenTarget() {
        Params originalParams = (Params) Params.inst.clone()
        try {
            Params.inst.rf_flatten = true
            Params.inst.rf_flatten_target = ""
            Params.inst.threads = 1

            Model model = trainSmallModel("FasterForest")
            // should be a no-op when target is empty
            Model result = new ModelConverter().applyConversions(model)
            assertTrue(result.classifier instanceof FasterForest, "Classifier should remain unchanged with empty target")
        } finally {
            Params.INSTANCE = originalParams
        }
    }

    @Test
    void testNonFlattableClassifierIsIgnored() {
        // Use a non-forest classifier that is not in FLATTABLE_CLASSIFIERS
        Model model = new Model("NonFlattable", new weka.classifiers.trees.J48())
        assertFalse(ModelConverter.isFlattableClassifier(model.classifier))

        Model result = new ModelConverter().flattenRandomForest(model, "FlatBinaryForest")
        // should return the original model unchanged
        assertSame(model, result)
    }

//===========================================================================================================//

    @Test
    void testBatchPredictionConsistency() {
        Model model = trainSmallModel("FasterForest")
        TrainableFasterForest trainable = (TrainableFasterForest) model.classifier
        double[][] testVectors = createTestVectors(100)

        for (ForestType type : [ForestType.FlatBinaryForest, ForestType.LegacyFlatBinaryForest, ForestType.InterleavedBfsForest]) {
            BinaryForest forest = FasterForestConverter.convertFasterForest(trainable, type)

            // single predictions should match batch predictions
            double[] batchResults = forest.predictForBatch(testVectors)
            for (int i = 0; i < testVectors.length; i++) {
                double single = forest.predict(testVectors[i])
                assertEquals(single, batchResults[i], 1e-12, "Single vs batch mismatch at $i for $type")
            }
        }
    }

    @Test
    void testModelInfoAfterFlattening() {
        Params originalParams = (Params) Params.inst.clone()
        try {
            Params.inst.rf_flatten = true
            Params.inst.rf_flatten_target = "FlatBinaryForest"
            Params.inst.threads = 1

            Model model = trainSmallModel("FasterForest")
            Model.Info infoBefore = model.getInfo()
            assertTrue(infoBefore.isForest)
            assertEquals(NUM_TREES, (int) infoBefore.numTrees)

            Model flattened = new ModelConverter().applyConversions(model)
            Model.Info infoAfter = flattened.getInfo()
            assertTrue(infoAfter.isForest)
            assertEquals(NUM_TREES, (int) infoAfter.numTrees)
            assertEquals(NUM_FEATURES + 1, (int) infoAfter.numFeatures)
        } finally {
            Params.INSTANCE = originalParams
        }
    }

}
