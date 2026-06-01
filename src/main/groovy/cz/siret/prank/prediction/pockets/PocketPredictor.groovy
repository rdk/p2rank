package cz.siret.prank.prediction.pockets

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.features.implementation.conservation.ConservationScore
import cz.siret.prank.geom.Atoms
import cz.siret.prank.prediction.pockets.clustering.ClusteringStrategy
import cz.siret.prank.prediction.transformation.ScoreTransformer
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Cutils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Calculates pockets from list of SAS points with ligandability scores.
 * (core of P2RANK algorithm)
 */
@Slf4j
@CompileStatic
class PocketPredictor implements Parametrized {

    private double POCKET_PROT_SURFACE_CUTOFF = params.pred_protein_surface_cutoff
    private double EXTENDED_POCKET_CUTOFF = params.extended_pocket_cutoff
    private boolean BALANCE_POINT_DENSITY = params.balance_density
    private double BALANCE_RADIUS = params.balance_density_radius
    private int SCORE_POINT_LIMIT = params.score_point_limit

    private ClusteringStrategy clusteringStrategy = ClusteringStrategy.create(params.clustering_strategy)

    // pocket score transformers
    private ScoreTransformer zscoreTpTransformer = ScoreTransformer.load(params.zscoretp_transformer)
    private ScoreTransformer probaTpTransformer = ScoreTransformer.load(params.probatp_transformer)

//===========================================================================================================//

    private double scorePoint(LabeledPoint point, Atoms surfacePoints) {

        double score = point.transformedScore

        if (BALANCE_POINT_DENSITY) {
            int pts = surfacePoints.countWithinSphere(point, BALANCE_RADIUS)  // count only: avoid materializing an Atoms list per point
            score = score / pts
        }

        return score
    }

    double pocketScore(Atoms pocketPoints, Atoms allSasPoints, Protein protein, Atoms pocketSurfaceAtoms)  {
        double score = 0
        try {
            List<LabeledPoint> sasPoints = pocketPoints.collect { (LabeledPoint)it }.toList()
            double[] pointScores = new double[sasPoints.size()]
            for (int i = 0; i < sasPoints.size(); i++) {
                pointScores[i] = scorePoint(sasPoints.get(i), allSasPoints)
            }

            Integer[] order = new Integer[sasPoints.size()]
            for (int i = 0; i < order.length; i++) order[i] = i
            Arrays.sort(order, { Integer a, Integer b -> Double.compare(pointScores[b], pointScores[a]) } as Comparator<Integer>)

            int limit = (SCORE_POINT_LIMIT > 0) ? Math.min(SCORE_POINT_LIMIT, order.length) : order.length
            for (int i = 0; i < limit; i++) {
                score += pointScores[order[i]]
            }

            if (params.score_pockets_by == "conservation" || params.score_pockets_by == "combi") {
                ConservationScore conservationScore = protein.conservationScore
                if (conservationScore != null) {
                    double avgConservation = pocketSurfaceAtoms.distinctGroupsSorted.stream()
                            .mapToDouble({
                        group -> conservationScore.getScoreForResidueSafe(group.getResidueNumber())
                    }).average().getAsDouble()
                    if (params.score_pockets_by == "conservation") {
                        score = avgConservation
                    } else {
                        score *= avgConservation
                    }
                }
            }
        } catch (ignored) {
            log.warn "Could not score pockets using [${params.score_pockets_by}]"
        }
        return score
    }

    /**
     *
     * @param allLabeledPoints list of points with predicted ligandability in hist[]
     * @param protein
     * @return
     */
    public List<? extends Pocket> predictPockets(List<LabeledPoint> allLabeledPoints, Protein protein) {

        Atoms labeledPoints = new Atoms(allLabeledPoints).withKdTree()

        log.info "PREDICTING POCKETS.... ===================================="
        log.info "SAS POINTS: {}", labeledPoints.count

        List<Atoms> clusters = clusteringStrategy.clusterPointsIntoPockets(allLabeledPoints)

        List<PrankPocket> pockets = clusters.collect { Atoms clusterPoints ->

            Atoms pocketPoints = clusterPoints
            if (EXTENDED_POCKET_CUTOFF > 0d) {
                Atoms extendedPocketPoints = labeledPoints.cutoutShell(clusterPoints, EXTENDED_POCKET_CUTOFF)
                pocketPoints = extendedPocketPoints
            }
            
            Atoms pocketSurfaceAtoms = protein.exposedAtoms.cutoutShell(pocketPoints, POCKET_PROT_SURFACE_CUTOFF)
            double score = pocketScore(pocketPoints, labeledPoints, protein, pocketSurfaceAtoms)

            Atoms pocketSasPoints = new Atoms( pocketPoints.collect { ((LabeledPoint)it).point }.toList() )  // we want exact objects from protein.accessibleSurface

            PrankPocket p = new PrankPocket(clusterPoints.centroid, score, pocketSasPoints, (List<LabeledPoint>)pocketPoints.list)
            p.surfaceAtoms = pocketSurfaceAtoms
            p.auxInfo.samplePoints = clusterPoints.count
            p.cache.count = clusterPoints.count

            if (zscoreTpTransformer!=null) {
                p.auxInfo.zScoreTP = zscoreTpTransformer.transformScore(score)
            }
            if (probaTpTransformer!=null) {
                p.auxInfo.probaTP = probaTpTransformer.transformScore(score)
            }

            return p
        }

        pockets = pockets.sort { // descending
            Pocket a, Pocket b -> b.newScore <=> a.newScore
        }

        int i = 0
        pockets.each {
            i++
            int count = it.cache.count as int
            double score = it.newScore
            int surfAtoms = it.surfaceAtoms.count
            log.info sprintf("pocket%2d -  surf_atoms: %3d   points: %3d   score: %6.1f", i, surfAtoms, count, score)
        }

        return pockets
    }

}
