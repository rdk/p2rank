package cz.siret.prank.score

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.features.chemproperties.ChemFeatureExtractor
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.rendering.LabeledPoint
import cz.siret.prank.score.prediction.PocketPredictor
import cz.siret.prank.score.results.ClassifierStats
import cz.siret.prank.utils.PerfUtils
import cz.siret.prank.utils.WekaUtils
import weka.classifiers.Classifier
import weka.core.DenseInstance
import weka.core.Instances

/**
 * Rescorer and Predictor
 * not thread safe
 */
@Slf4j
@CompileStatic
class WekaSumRescorer extends PocketRescorer implements Parametrized  {

    private final double POSITIVE_POINT_LIGAND_DISTANCE = params.positive_point_ligand_distance
    private final double PSP = params.point_score_pow

    private FeatureExtractor extractorFactory
    private Classifier  classifier
    private ClassifierStats stats = new ClassifierStats(2)

    boolean collectPoints = params.visualizations || params.predictions
    boolean visAllSurface = params.vis_all_surface

    // Connolly points with ligandability score for prediction and visualization
    List<LabeledPoint> labeledPoints = new ArrayList<>()

    // auxiliary for weka
    private Instances auxWekaDataset
    private double[] alloc
    private DenseInstance auxInst

    WekaSumRescorer(Classifier classifier, FeatureExtractor extractorFactory) {
        this.extractorFactory = extractorFactory
        this.classifier = classifier

        auxWekaDataset = WekaUtils.createDatasetWithBinaryClass(extractorFactory.vectorHeader)
    }

    /**
     * @param prediction
     * @param ligandedProtein can be null - in not used for evaluating statistics
     */
    @Override
    void rescorePockets(Prediction prediction) {

        FeatureExtractor proteinExtractor = extractorFactory.createPrototypeForProtein(prediction.protein)

        alloc = new double[proteinExtractor.vectorHeader.size() + 1] // one additional for stupid weka class
        auxInst = new DenseInstance( 1, alloc )
        auxInst.setDataset(auxWekaDataset)

        // PRANK (just rescoring existing pockets)
        if (!params.predictions) {
            doRescore(prediction, proteinExtractor)
        }

        // compute ligandability scores of connolly points for predictions and visualization
        if (params.predictions || visAllSurface) {

            FeatureExtractor extractor = (proteinExtractor as ChemFeatureExtractor).createInstanceForWholeProtein()

            labeledPoints = new ArrayList<>(extractor.sampledPoints.count)
            for (Atom point in extractor.sampledPoints) {
                labeledPoints.add(new LabeledPoint(point))
            }

            for (LabeledPoint point in labeledPoints) {

                // classification

                FeatureVector props = extractor.calcFeatureVector(point)
                point.@hist = getDistributionForPoint(classifier, props)

                // labels and statistics

                double[] prob = point.hist

                boolean predicted = prob[1] > prob[0]
                boolean observed = false

                if (ligandAtoms!=null) {
                    double closestLigandDistance = ligandAtoms.count > 0 ? ligandAtoms.dist(point) : Double.MAX_VALUE
                    observed = (closestLigandDistance <= POSITIVE_POINT_LIGAND_DISTANCE)
                }

                if (collectingStatistics) {
                    stats.addCase(observed, predicted, prob[1])
                }
            }

            // generate predictions
            if (params.predictions) {
                prediction.pockets = new PocketPredictor().predictPockets(labeledPoints, prediction.protein)
                prediction.reorderedPockets = prediction.pockets
                prediction.labeledPoints = labeledPoints
            }
        }

    }

    private void doRescore(Prediction prediction, FeatureExtractor proteinExtractor) {

        for (Pocket pocket in prediction.pockets) {
            FeatureExtractor extractor = proteinExtractor.createInstanceForPocket(pocket)

            double sum = 0
            double rawSum = 0

            for (Atom point in extractor.sampledPoints) {

                FeatureVector props = extractor.calcFeatureVector(point)

                double[] prob = getDistributionForPoint(classifier, props)
                double result = prob[1]
                boolean predicted = prob[1] > prob[0]
                boolean observed = false

                if (collectingStatistics) {
                    double closestLigandDistance = ligandAtoms.count > 0 ? ligandAtoms.dist(point) : Double.MAX_VALUE
                    observed = (closestLigandDistance <= POSITIVE_POINT_LIGAND_DISTANCE)
                    stats.addCase(observed, predicted, prob[1])

                }
                if (collectPoints) {
                    labeledPoints.add(new LabeledPoint(point, prob, observed, predicted))
                }

                sum += Math.pow(result, PSP)

                rawSum += prob[1] / (prob[0]+prob[1]) // =P(ligandable)
            }

            double score = sum
            pocket.newScore = score
            pocket.auxInfo.rawNewScore = rawSum / extractor.sampledPoints.count // ratio of predicted ligandable points
            pocket.auxInfo.samplePoints = extractor.sampledPoints.count
        }

    }

    private final double[] getDistributionForPoint(Classifier classifier, FeatureVector prop) {
        PerfUtils.toPrimitiveArray(prop.vector, alloc)
        return classifier.distributionForInstance(auxInst)
    }

}
