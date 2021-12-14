package cz.siret.prank.domain.labeling

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.features.PrankFeatureExtractor
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.geom.Atoms
import cz.siret.prank.prediction.metrics.ClassifierStats
import cz.siret.prank.prediction.pockets.PointScoreCalculator
import cz.siret.prank.prediction.pockets.rescorers.InstancePredictor
import cz.siret.prank.program.PrankException
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

import static cz.siret.prank.prediction.pockets.PointScoreCalculator.applyPointScoreThreshold

/**
 *
 */
@CompileStatic
class ModelBasedPointLabeler extends PointLabeler implements Parametrized {

    private Model model
    private ProcessedItemContext context

    private ClassifierStats classifierStats = new ClassifierStats()

    private final PointScoreCalculator calculator = new PointScoreCalculator()
    private final boolean USE_ONLY_POSITIVE_SCORE = params.use_only_positive_score


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
        InstancePredictor instancePredictor = InstancePredictor.create(model, proteinExtractor)

        // init result array
        final List<LabeledPoint> labeledPoints = new ArrayList<LabeledPoint>(points.count)
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
            // classification

            FeatureVector vect = extractor.calcFeatureVector(point.point)


            calculator.scorePoint(point, vect, instancePredictor)


            // labels and statistics

            boolean predicted = binaryLabel(point.score)
            boolean observed = false

            if (observedPoints != null) {
                observed = observedPoints[i].observed
            }

            point.predicted = predicted
            point.observed = observed

            if (collectingStats) {
                classifierStats.addPrediction(observed, predicted, point.score)
            }

            i++
        }

        // TODO don't finalize when doing repeated experiments
        // finalize
        proteinExtractor.finalizeProteinPrototype()

        return labeledPoints
    }

    static boolean binaryLabel(double predictedScore) {
        applyPointScoreThreshold(predictedScore)
    }

}
