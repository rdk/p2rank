package cz.siret.prank.domain.labeling

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.features.PrankFeatureExtractor
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.geom.Atoms
import cz.siret.prank.prediction.metrics.ClassifierStats
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.PerfUtils
import cz.siret.prank.utils.WekaUtils
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import weka.core.DenseInstance
import weka.core.Instances

import static cz.siret.prank.prediction.pockets.PointScoreCalculator.applyPointScoreThreshold
import static cz.siret.prank.prediction.pockets.PointScoreCalculator.predictedScore

/**
 *
 */
@CompileStatic
class ModelBasedPointLabeler extends PointLabeler {

    private Model model
    private ProcessedItemContext context

    private ClassifierStats classifierStats = new ClassifierStats()

    // auxiliary for weka
    private Instances auxWekaDataset
    private double[] alloc
    private DenseInstance auxInst

    private List<LabeledPoint> observedPoints = null
    

    ModelBasedPointLabeler(Model model, ProcessedItemContext context) {
        this.model = model
        this.context = context
    }

    ModelBasedPointLabeler withObserved(List<LabeledPoint> observedPoints) {
        this.observedPoints = observedPoints
        return this
    }

    ClassifierStats getClassifierStats() {
        return classifierStats
    }

    /**
     *
     * @param points
     * @param protein
     * @return
     */
    @CompileStatic
    @Override
    List<LabeledPoint> labelPoints(Atoms points, Protein protein) {

        // init extractor
        // TODO: refactor messy FeatureExtractor api
        FeatureExtractor extractorFactory = FeatureExtractor.createFactory()
        FeatureExtractor proteinExtractor = extractorFactory.createPrototypeForProtein(protein, context)
        FeatureExtractor extractor = (proteinExtractor as PrankFeatureExtractor).createInstanceForWholeProtein(points)

        // init weka
        alloc = new double[proteinExtractor.vectorHeader.size() + 1] // one additional for stupid weka class
        auxInst = new DenseInstance( 1, alloc )
        auxWekaDataset = WekaUtils.createDatasetWithBinaryClass(extractorFactory.vectorHeader)
        auxInst.setDataset(auxWekaDataset)

        // init result array
        final List<LabeledPoint> labeledPoints = new ArrayList<LabeledPoint>(extractor.sampledPoints.count)
        for (Atom point : points) {
            labeledPoints.add(new LabeledPoint(point))
        }

        // potentially sync with observed
        boolean collectingStats = false
        if (observedPoints != null) {
            collectingStats = true
            if (observedPoints.size() != labeledPoints.size()) {
                throw new PrankException("Point counts do not match! [observed:${observedPoints.size()} to_predict:${labeledPoints.size()}]")
            }
        }

        // label
        int i = 0
        for (LabeledPoint point : labeledPoints) {
        //labeledPoints.each { LabeledPoint point ->
            // classification

            FeatureVector props = extractor.calcFeatureVector(point.point)
            double[] hist = getDistributionForPoint(model, props)

            // labels and statistics

            double predictedScore = predictedScore(hist)   // not all classifiers give histogram that sums up to 1
            boolean predicted = binaryLabel(predictedScore)
            boolean observed = false

            if (observedPoints != null) {
                observed = observedPoints[i].observed
            }

            point.@hist = hist
            point.@predicted = predicted
            point.@observed = observed
            point.@score = predictedScore

            if (collectingStats) {
                classifierStats.addPrediction(observed, predicted, predictedScore, hist)
            }

            i++
        }

        // finalize
        proteinExtractor.finalizeProteinPrototype()

        return labeledPoints
    }

    static boolean binaryLabel(double predictedScore) {

        
        applyPointScoreThreshold(predictedScore)
    }

    private final double[] getDistributionForPoint(Model model, FeatureVector vect) {
//        if (classifier instanceof FasterForest) {
//            return ((FasterForest)classifier).distributionForAttributes(vect.array, 2)
//        } else {
        PerfUtils.arrayCopy(vect.array, alloc)
        return model.classifier.distributionForInstance(auxInst)
//        }

    }

}
