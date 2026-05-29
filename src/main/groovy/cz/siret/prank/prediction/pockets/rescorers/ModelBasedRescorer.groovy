package cz.siret.prank.prediction.pockets.rescorers

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.domain.labeling.ResidueLabelings
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.FeatureVector
import cz.siret.prank.features.PrankFeatureExtractor
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.geom.Atoms
import cz.siret.prank.prediction.metrics.ClassifierStats
import cz.siret.prank.prediction.pockets.PocketPredictor
import cz.siret.prank.prediction.pockets.PointScoreCalculator
import cz.siret.prank.prediction.transformation.ScoreTransformer
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.ml.ModelCompatibility
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.routines.predict.output.PointExportData
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import static cz.siret.prank.prediction.pockets.PointScoreCalculator.applyPointScoreThreshold

/**
 * rescorer and predictor
 *
 * Not thread safe!
 *
 * This is the main rescore used by P2RANK to make predictions based on machine learning
 *
 */
@Slf4j
@CompileStatic
class ModelBasedRescorer extends PocketRescorer implements Parametrized  {

    private final double POSITIVE_POINT_LIGAND_DISTANCE = params.positive_point_ligand_distance

    private final PointScoreCalculator calculator = new PointScoreCalculator()

    private FeatureExtractor extractorFactory
    private Model model
    private ClassifierStats stats = new ClassifierStats()

    boolean collectPoints = params.visualizations || params.predictions
    boolean visualizeAllSurface = params.vis_all_surface

    // SAS points with ligandability score for prediction and visualization
    List<LabeledPoint> labeledPoints = new ArrayList<>()

    // Stored during rescorePockets for use by residue labeling after filtering
    private Atoms sampledSasPoints

    // Data for point export (null if export disabled)
    PointExportData exportData = null


    ModelBasedRescorer(Model model, FeatureExtractor extractorFactory) {
        this.extractorFactory = extractorFactory
        this.model = model

        // Fail fast (or warn) if the loaded model was trained with a different feature header than
        // the one the current configuration produces, which would otherwise yield silently wrong predictions.
        if (extractorFactory instanceof PrankFeatureExtractor) {
            ModelCompatibility.check(model, ((PrankFeatureExtractor) extractorFactory).vectorHeader, params.fail_on_model_feature_mismatch)
        }
    }

    /**
     * @param prediction
     */
    @Override
    void rescorePockets(Prediction prediction, ProcessedItemContext context) {

        FeatureExtractor proteinExtractor = extractorFactory.createPrototypeForProtein(prediction.protein, context)

        InstancePredictor instancePredictor = InstancePredictor.create(model, proteinExtractor)

        // PRANK (just rescoring existing pockets)
        if (!params.predictions) {
            doRescore(prediction, proteinExtractor, instancePredictor)
        }

        // compute ligandability scores of SAS points for predictions and visualization
        if (params.predictions || visualizeAllSurface) {

            FeatureExtractor extractor = (proteinExtractor as PrankFeatureExtractor).createInstanceForWholeProtein()
            sampledSasPoints = extractor.sampledPoints.points

            int n_points = sampledSasPoints.count
            labeledPoints = new ArrayList<>(n_points)
            for (Atom point : extractor.sampledPoints.points) {
                labeledPoints.add(new LabeledPoint(point))
            }

            List<FeatureVector> vectors = new ArrayList<>(n_points)
            for (LabeledPoint point : labeledPoints) {
                vectors.add(extractor.calcFeatureVector(point.point))
            }

            // classification
            double[] scores = instancePredictor.predictBatch(vectors)

            // Capture export data if enabled
            if (params.export_points) {
                exportData = PointExportData.create(labeledPoints, vectors, extractor.vectorHeader)
            }

            // TODO refactor: use ModelBasedPointLabeler instead of this loop
            for (int i=0; i!=n_points; ++i) {
                LabeledPoint point = labeledPoints.get(i)

                // labels and statistics
                calculator.scorePoint(point, scores[i])

                point.predicted = applyPointScoreThreshold(point.score)
                point.observed = isPositivePoint(point.point, ligandAtoms)

                if (collectStats) {
                    stats.addPrediction(point.observed, point.predicted, point.score)
                }
            }

            // generate predictions
            if (params.predictions) {
                prediction.pockets = new PocketPredictor().predictPockets(labeledPoints, prediction.protein)
                prediction.outputPockets = new ArrayList<>(prediction.pockets)
                prediction.labeledPoints = labeledPoints
            }
        }

        proteinExtractor.finalizeProteinPrototype()
    }

    @Override
    void reorderPockets(Prediction prediction, ProcessedItemContext context) {
        super.reorderPockets(prediction, context)

        // Residue labeling runs AFTER filtering + finalization so that pocket.rank
        // values in the residue CSV match the final filtered output.
        if (params.predictions && params.label_residues) {
            if (sampledSasPoints == null || labeledPoints == null) {
                throw new IllegalStateException("rescorePockets must run before residue labeling")
            }
            prediction.residueLabelings = ResidueLabelings.calculate(
                prediction, model, sampledSasPoints, labeledPoints, context
            )
        }
    }

    boolean isPositivePoint(Atom point, Atoms ligandAtoms) {
        if (ligandAtoms == null || ligandAtoms.empty) {
            return false
        }
        return ligandAtoms.dist(point) <= POSITIVE_POINT_LIGAND_DISTANCE
    }

    /**
     * Rescore predictions of other methods
     * TODO refactor to use PointScoreCalculator
     */
    private void doRescore(Prediction prediction, FeatureExtractor proteinExtractor, InstancePredictor instancePredictor) {

        if (params.bench_skip_rescoring) {
            // Null-op for benchmarking only — skip feature extraction and ML scoring.
            // pocket.newScore passes through; downstream routines still see consistent state.
            for (Pocket pocket : prediction.pockets) {
                pocket.newScore = pocket.score
                pocket.auxInfo.rawNewScore = pocket.score
                pocket.auxInfo.samplePoints = 0
            }
            return
        }

        proteinExtractor.prepareProteinPrototypeForPockets()

        // pocket score transformers
        ScoreTransformer probaTpTransformer = ScoreTransformer.load(params.probatp_transformer)

        // Initialize export builder if enabled (for rescore mode, exports pocket points only)
        boolean doExport = params.export_points
        PointExportData.Builder exportBuilder = null

        for (Pocket pocket : prediction.pockets) {
            FeatureExtractor extractor = proteinExtractor.createInstanceForPocket(pocket)

            // Initialize export builder with header from first extractor
            if (doExport && exportBuilder == null) {
                exportBuilder = PointExportData.builder(extractor.vectorHeader)
            }

            double sum = 0
            double rawSum = 0

            List<LabeledPoint> pocketLabeledPoints = new ArrayList<>(extractor.sampledPoints.points.count)

            for (Atom point : extractor.sampledPoints.points) {

                FeatureVector vector = extractor.calcFeatureVector(point)

                // not all classifiers give histogram that sums up to 1
                double pointScore = instancePredictor.predictPositive(vector)
                boolean predicted = applyPointScoreThreshold(pointScore)
                boolean observed = false

                if (collectStats) {
                    observed = isPositivePoint(point, ligandAtoms)
                    stats.addPrediction(observed, predicted, pointScore)
                }

                LabeledPoint labeledPoint = new LabeledPoint(point, observed, predicted, pointScore)
                pocketLabeledPoints.add(labeledPoint)

                // Capture export data
                if (doExport) {
                    exportBuilder.add(labeledPoint, vector)
                }

                sum += calculator.transformScore(pointScore)

                rawSum += pointScore // ~ P(ligandable)
            }

            if (collectPoints) {
                labeledPoints.addAll(pocketLabeledPoints)
            }

            double pocketScore = sum
            pocket.newScore = pocketScore
            pocket.sasPoints = extractor.sampledPoints.points
            pocket.labeledPoints = pocketLabeledPoints
            pocket.auxInfo.rawNewScore = rawSum / extractor.sampledPoints.points.count // ratio of predicted ligandable points
            pocket.auxInfo.samplePoints = extractor.sampledPoints.points.count

            if (probaTpTransformer!=null) {
                pocket.auxInfo.probaTP = probaTpTransformer.transformScore(pocketScore)
            }
        }

        // Finalize export data
        if (doExport && exportBuilder != null) {
            exportData = exportBuilder.build()
        }
    }

    ClassifierStats getStats() {
        return stats
    }

}
