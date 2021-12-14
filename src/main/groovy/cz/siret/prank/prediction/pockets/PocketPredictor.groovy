package cz.siret.prank.prediction.pockets

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.features.implementation.conservation.ConservationScore
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
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

    private final PointScoreCalculator pointScoreCalculator = new PointScoreCalculator()

    private double POCKET_PROT_SURFACE_CUTOFF = params.pred_protein_surface_cutoff
    private int MIN_CLUSTER_SIZE = params.pred_min_cluster_size
    private double EXTENDED_POCKET_CUTOFF = params.extended_pocket_cutoff
    private double CLUSTERING_DIST = params.pred_clustering_dist
    private double POINT_THRESHOLD = params.pred_point_threshold
    private boolean BALANCE_POINT_DENSITY = params.balance_density
    private double BALANCE_RADIUS = params.balance_density_radius
    private int SCORE_POINT_LIMIT = params.score_point_limit

    private double scorePoint(LabeledPoint point, Atoms surfacePoints) {

        //double score = pointScoreCalculator.transformScore(point.score)
        double score = point.transformedScore

        if (BALANCE_POINT_DENSITY) {
            int pts = surfacePoints.cutoutSphere(point, BALANCE_RADIUS).count
            score = score / pts
        }

        return score
    }

    private boolean admitPoint(LabeledPoint point) {
//        double p = PointScoreCalculator.predictedScore(point.hist)
//        return p > POINT_THRESHOLD
        point.predicted
    }

    double pocketScore(Atoms pocketPoints, Atoms allSasPoints, Protein protein, Atoms pocketSurfaceAtoms)  {
        double score = 0
        try {
            List<LabeledPoint> sasPoints = pocketPoints.collect { (LabeledPoint)it }.toList()
            for (LabeledPoint p : sasPoints) {
                p.score = scorePoint(p, allSasPoints)
            }

            sasPoints = sasPoints.sort { // descending
                LabeledPoint a, LabeledPoint b -> b.score <=> a.score
            }

            List<LabeledPoint> scoringPoints = sasPoints
            if (SCORE_POINT_LIMIT > 0) {
                scoringPoints = Cutils.head(SCORE_POINT_LIMIT, sasPoints)
            }

            score = (double) scoringPoints.collect { it.score }.sum(0)

            if (params.score_pockets_by == "conservation" || params.score_pockets_by == "combi") {
                if (protein.secondaryData.getOrDefault(ConservationScore.CONSERV_LOADED_KEY,
                        false)) {
                    ConservationScore conservationScore = (ConservationScore) protein.secondaryData.get(ConservationScore.CONSERV_SCORE_KEY)
                    double avgConservation = pocketSurfaceAtoms.distinctGroupsSorted.stream()
                            .mapToDouble({
                        group -> conservationScore.getScoreForResidue(group.getResidueNumber())
                    }).average().getAsDouble()
                    if (params.score_pockets_by == "conservation") {
                        score = avgConservation
                    } else {
                        score *= avgConservation
                    }
                }
            }
        } catch (ignored){
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

        // filter
        List<LabeledPoint> ligandablePoints = allLabeledPoints.findAll { admitPoint(it) }.toList()
        List<Atoms> clusters = Struct.clusterAtoms(new Atoms(ligandablePoints), CLUSTERING_DIST)
        List<Atoms> filteredClusters = clusters.findAll { it.count >= MIN_CLUSTER_SIZE  }.toList()

        log.info "PREDICTING POCKETS.... ===================================="
        log.info "SAS POINTS: {}", labeledPoints.count
        log.info "LIGANDABLE POINTS: {}", ligandablePoints.size()
        log.info "CLUSTERS: {}", clusters.size()
        log.info "FILTERED CLUSTERS: {}", filteredClusters.size()

        // score transformers
        ScoreTransformer zscoreTpTransformer = ScoreTransformer.load(params.zscoretp_transformer)
        ScoreTransformer probaTpTransformer = ScoreTransformer.load(params.probatp_transformer)

        List<PrankPocket> pockets = filteredClusters.collect { Atoms clusterPoints ->

            Atoms pocketPoints = clusterPoints
            if (EXTENDED_POCKET_CUTOFF > 0d) {
                Atoms extendedPocketPoints = labeledPoints.cutoutShell(clusterPoints, EXTENDED_POCKET_CUTOFF)
                pocketPoints = extendedPocketPoints
            }
            
//          double score = (double) pocketPoints.collect { scorePoint((LabeledPoint)it, allSasPoints) }.sum(0)
            Atoms pocketSurfaceAtoms = protein.exposedAtoms.cutoutShell(pocketPoints, POCKET_PROT_SURFACE_CUTOFF)
            double score = pocketScore(pocketPoints, labeledPoints, protein, pocketSurfaceAtoms)

            Atoms pocketSasPoints = new Atoms( pocketPoints.collect { ((LabeledPoint)it).point }.toList() )  // we want exact objects from protein.accessibleSurface

            PrankPocket p = new PrankPocket(clusterPoints.centroid, score, pocketSasPoints, (List<LabeledPoint>) pocketPoints.list)
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

            for (LabeledPoint lp : it.labeledPoints) {
                if (lp.score > 0.2) { // TODO XXX this is temporary to fix pymol visualization esthetics
                    lp.pocket = i
                }
            }

            it.name = "pocket" + i
            it.rank = i

            int count = it.cache.count as int
            double score = it.newScore
            int surfAtoms = it.surfaceAtoms.count
            log.info sprintf("pocket%2d -  surf_atoms: %3d   points: %3d   score: %6.1f", i, surfAtoms, count, score)
        }

        return pockets
    }

}
