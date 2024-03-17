package cz.siret.prank.domain.labeling

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.Residues
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.geom.Atoms
import cz.siret.prank.prediction.metrics.ClassifierStats
import cz.siret.prank.prediction.pockets.PointScoreCalculator
import cz.siret.prank.program.ml.Model
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Cutils
import cz.siret.prank.utils.MathUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.annotation.concurrent.NotThreadSafe
import java.util.function.Function

import static cz.siret.prank.utils.Cutils.mapList
import static cz.siret.prank.utils.Formatter.format
import static cz.siret.prank.utils.Formatter.formatNumbers

/**
 * (not intended to be reused with more proteins)
 */
@Slf4j
@NotThreadSafe
@CompileStatic
class ModelBasedResidueLabeler extends ResidueLabeler<Boolean> implements Parametrized {

    private Model model
    private Atoms sampledPoints
    private ProcessedItemContext context

    private List<LabeledPoint> labeledPoints
    private List<LabeledPoint> observedPoints = null

    private ClassifierStats classifierStats
    private ResidueLabeling<Double> doubleLabeling


    private final PointScoreCalculator pointScoreCalculator = new PointScoreCalculator()
    private final double SCORE_THRESHOLD = params.residue_score_threshold
    private final double RADIUS = params.getSasCutoffDist() + params.residue_score_extra_dist
    private final double SUM_TO_AVG_POW = params.residue_score_sum_to_avg
    private final boolean ONLY_EXPOSED = params.residue_score_only_exposed

    private Function<Double, Double> residueScoreTransform

    ModelBasedResidueLabeler(Model model, Atoms sampledPoints, ProcessedItemContext context) {
        this.model = model
        this.sampledPoints = sampledPoints
        this.context = context

        if (params.residue_score_transform == "SIGMOID") {
            residueScoreTransform = new Function<Double, Double>() {
                @Override
                Double apply(Double x) {
                    return MathUtils.sigmoid01(x)
                }
            }
        } else {
            residueScoreTransform = Function.identity()
        }
    }

    ModelBasedResidueLabeler withObserved(List<LabeledPoint> observedPoints) {
        this.observedPoints = observedPoints
        return this
    }

    List<LabeledPoint> getLabeledPoints() {
        return labeledPoints
    }

    ClassifierStats getClassifierStats() {
        return classifierStats
    }

    ResidueLabeling<Double> getDoubleLabeling() {
        return doubleLabeling
    }

    @Override
    ResidueLabeling<Boolean> labelResidues(Residues residues, Protein protein) {

        ModelBasedPointLabeler predictor = new ModelBasedPointLabeler(model, context).withObserved(observedPoints)

        // avoid repetitive training and optimization when optimized params do not influence it
        if (params.hopt_cache_labeled_points) {
            protein.secondaryData.computeIfAbsent('saved_labeled_points', { k -> predictor.labelPoints(sampledPoints, protein) })
            labeledPoints = (List<LabeledPoint>) protein.secondaryData.get('saved_labeled_points')
        } else {
            labeledPoints = predictor.labelPoints(sampledPoints, protein)
        }

        classifierStats = predictor.classifierStats

        return calculateLabeling(residues, labeledPoints, protein)
    }

    /**
     * calculates doubleLabeling as well
     */
    BinaryLabeling calculateLabeling(Residues residues, List<LabeledPoint> labeledPoints, Protein protein) {

        Atoms points = new Atoms(labeledPoints)

        // calculate binary labels by sum and threshold

        ResidueLabeling<Double> resScores = new ResidueLabeling<>(residues.count)

        for (Residue res : residues) {
            List<Double> pscores = Collections.emptyList()
            if (!ONLY_EXPOSED || protein.exposedResidues.contains(res)) {
                pscores = mapList(points.cutoutShell(res.atoms, RADIUS).<LabeledPoint>asList(), { it.score })
            }
            
            double score = aggregateScore(pscores)

            log.trace "aggregated_score = {}", score

            score = transformScore(score)

            log.trace "transformed_score = {}", score

            if (log.traceEnabled) {
                log.trace "RES[{}] (score={}) pscores(n={}): {}", res, format(score, 2), pscores.size(), formatNumbers(pscores, 2)
            }

            resScores.add(res, score)
        }
        doubleLabeling = resScores

        BinaryLabeling resLabels = new BinaryLabeling(residues.count)

        for (LabeledResidue<Double> it : resScores.labeledResidues) {
            resLabels.add(it.residue, binaryLabel(it.label))
        }

        return resLabels
    }

    private boolean binaryLabel(double score) {
        score >= SCORE_THRESHOLD
    }

    private double aggregateScore(List<Double> scores) {
        if (scores.empty) return 0d

        int limit = params.score_point_limit
        if (limit > 0) {
            if (scores.size() > limit) {
                scores = Cutils.head(limit, scores.toSorted().reverse())
            }
        }

        List<Double> transformedScores = scores.collect { pointScoreCalculator.transformScore(it) }

        if (log.traceEnabled) {
            log.trace "transformed_scores(n={}): {}", transformedScores.size(), formatNumbers(transformedScores, 2)
        }

        double sum = Cutils.sum(transformedScores)

        log.trace "sum_score = {}", sum

        double base = scores.size()
        base = Math.pow(base, SUM_TO_AVG_POW) // exp. of <0,1> goes from 'no average, just sum' -> 'full average'

        log.trace "base = {}", base

        double score = sum / base

        //log.warn "AGG:{} from {}", score, scores // XXX

        return score
    }

    private double transformScore(double score) {
        return residueScoreTransform.apply(score)
    }

    @Override
    boolean isBinary() {
        return true
    }

}
