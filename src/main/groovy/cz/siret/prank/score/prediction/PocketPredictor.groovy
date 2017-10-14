package cz.siret.prank.score.prediction

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.features.implementation.conservation.ConservationScore
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.rendering.LabeledPoint
import cz.siret.prank.utils.CollectionUtils
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 * Calculates pockets from list of SAS points with ligandability scores.
 * (core of P2RANK algorithm)
 */
@Slf4j
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

        double score = pointScoreCalculator.transformedPointScore(point.hist)

        if (BALANCE_POINT_DENSITY) {
            int pts = surfacePoints.cutoffAroundAtom(point, BALANCE_RADIUS).count
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
                scoringPoints = CollectionUtils.head(SCORE_POINT_LIMIT, sasPoints)
            }

            score = (double) scoringPoints.collect { it.score }.sum(0)

            if (params.score_pockets_by == "conservation" || params.score_pockets_by == "combi") {
                if (protein.secondaryData.getOrDefault(ConservationScore.conservationLoadedKey,
                        false)) {
                    ConservationScore conservationScore = protein.secondaryData.get(ConservationScore.conservationScoreKey)
                    double avgConservation = pocketSurfaceAtoms.distinctGroups.stream()
                            .mapToDouble({
                        group -> conservationScore.getScoreForResidue(group.getResidueNumber())
                    }).average().getAsDouble()
                    if (params.score_pockets_by == "conservation") {
                        score = avgConservation;
                    } else {
                        score *= avgConservation;
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
     * @param connollyPointList list of points with predicted ligandability in hist[]
     * @param protein
     * @return
     */
    public List<Pocket> predictPockets(List<LabeledPoint> connollyPointList, Protein protein) {

        Atoms allSasPoints = new Atoms(connollyPointList).withKdTree()

        // filter
        List<LabeledPoint> ligandablePoints = allSasPoints.list.findAll { admitPoint(it) }.toList()
        List<Atoms> clusters = Struct.clusterAtoms(new Atoms(ligandablePoints), CLUSTERING_DIST)
        List<Atoms> filteredClusters = clusters.findAll { it.count >= MIN_CLUSTER_SIZE  }.toList()

        log.info "PREDICTING POCKETS.... ===================================="
        log.info "SAS POINTS: {}", allSasPoints.count
        log.info "LIGANDABLE POINTS: {}", ligandablePoints.size()
        log.info "CLUSTERS: {}", clusters.size()
        log.info "FILTERED CLUSTERS: {}", filteredClusters.size()

        List<PrankPocket> pockets = filteredClusters.collect { Atoms clusterPoints ->

            Atoms pocketPoints = clusterPoints
            if (EXTENDED_POCKET_CUTOFF > 0d) {
                Atoms extendedPocketPoints = allSasPoints.cutoffAtoms(clusterPoints, EXTENDED_POCKET_CUTOFF)
                pocketPoints = extendedPocketPoints
            }
            
//            double score = (double) pocketPoints.collect { scorePoint((LabeledPoint)it, allSasPoints) }.sum(0)
            Atoms pocketSurfaceAtoms = protein.exposedAtoms.cutoffAtoms(pocketPoints, POCKET_PROT_SURFACE_CUTOFF)
            double score = pocketScore(pocketPoints, allSasPoints, protein, pocketSurfaceAtoms)

            PrankPocket p = new PrankPocket(clusterPoints.centerOfMass, score, clusterPoints) // or pocketPoints ?
            p.surfaceAtoms = pocketSurfaceAtoms
            p.auxInfo.samplePoints = clusterPoints.count
            p.cache.count = clusterPoints.count
            return p
        }

        pockets = pockets.sort { // descending
            Pocket a, Pocket b -> b.newScore <=> a.newScore
        }

        int i = 0
        pockets.each {
            i++

            for (Atom a : it.sasPoints) {
                ((LabeledPoint) a).@pocket = i
            }

            it.name = "pocket" + i

            int count = it.cache.count
            double score = it.newScore
            int surfAtoms = it.surfaceAtoms.count
            log.info sprintf("pocket%2d -  surf_atoms: %3d   points: %3d   score: %6.1f", i, surfAtoms, count, score)
        }

        return pockets;
    }

}
