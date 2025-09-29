package cz.siret.prank.prediction.pockets.clustering

import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 *
 */
@Slf4j
@CompileStatic
class SingleLinkageClustering extends ClusteringStrategy implements Parametrized {

    final double clusteringDist = params.pred_clustering_dist
    final int minClusterSize = params.pred_min_cluster_size

    @Override
    List<Atoms> clusterPointsIntoPockets(List<LabeledPoint> points) {

        List<LabeledPoint> ligandablePoints = points.findAll { admitPoint(it) }.toList()
        List<Atoms> clusters = Struct.clusterAtoms(new Atoms(ligandablePoints), clusteringDist)
        List<Atoms> filteredClusters = clusters.findAll { it.count >= minClusterSize  }.toList()

        log.info "LIGANDABLE POINTS: {}", ligandablePoints.size()
        log.info "CLUSTERS: {}", clusters.size()
        log.info "FILTERED CLUSTERS: {}", filteredClusters.size()

        return filteredClusters
    }

    private static boolean admitPoint(LabeledPoint point) {
        point.predicted
    }

}
