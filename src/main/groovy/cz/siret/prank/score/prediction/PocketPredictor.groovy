package cz.siret.prank.score.prediction

import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.rendering.LabeledPoint

/**
 * Calculates pockets from list of Connolly points with ligandability scores
 */
@Slf4j
class PocketPredictor implements Parametrized {

    double PSP = params.point_score_pow
    double POCKET_PROT_SURFACE_CUTOFF = params.pred_protein_surface_cutoff
    int MIN_CLUSTER_SIZE = params.pred_min_cluster_size
    double SURROUNDING_DIST = params.pred_surrounding
    double CLUSTERING_DIST = params.pred_clustering_dist
    double POINT_THRESHOLD = params.pred_point_threshold
    boolean BALANCE_POINT_DENSITY = params.balance_density
    double BALANCE_RADIUS = params.balance_density_radius

    private double score(LabeledPoint point, Atoms surfacePoints) {
        //double p = point.hist[1] / (point.hist[0] + point.hist[1])
        double p = point.hist[1]
        double score = Math.pow(p, PSP)

        if (BALANCE_POINT_DENSITY) {
            int pts = surfacePoints.cutoffAtomsAround(point, BALANCE_RADIUS).count
            score = score / pts
        }

        return score
    }

    private boolean admitPoint(LabeledPoint point) {

        double p = point.hist[1] / (point.hist[0] + point.hist[1])
        return p > POINT_THRESHOLD
    }

    /**
     *
     * @param connollyPointList list of points with predicted ligandability in hist[]
     * @param protein
     * @return
     */
    public List<Pocket> predictPockets(List<LabeledPoint> connollyPointList, Protein protein) {

        Atoms connollyPoints = new Atoms(connollyPointList).withKdTree()

        // filter
        List<LabeledPoint> ligandablePoints = connollyPoints.list.findAll { admitPoint(it) }.toList()
        List<Atoms> clusters = Struct.clusterAtoms(new Atoms(ligandablePoints), CLUSTERING_DIST)
        List<Atoms> filteredClusters = clusters.findAll { it.count >= MIN_CLUSTER_SIZE  }.toList()

        log.info "PREDICTING POCKETS.... ===================================="
        log.info "CONOLLY POINTS: {}", connollyPoints.count
        log.info "LIGANDABLE POINTS: {}", ligandablePoints.size()
        log.info "CLUSTERS: {}", clusters.size()
        log.info "FILTERED CLUSTERS: {}", filteredClusters.size()

        List<PrankPocket> pockets = filteredClusters.collect { Atoms clusterPoints ->

            Atoms extendedPocketPoints = connollyPoints.cutoffAtoms(clusterPoints, SURROUNDING_DIST)
            double score = extendedPocketPoints.collect { score((LabeledPoint)it, connollyPoints) }.sum()
            Atoms pocketSurfaceAtoms = protein.exposedAtoms.cutoffAtoms(extendedPocketPoints, POCKET_PROT_SURFACE_CUTOFF)

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

            for (Atom a : it.innerPoints) {
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
