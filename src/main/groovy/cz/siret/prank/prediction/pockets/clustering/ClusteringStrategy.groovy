package cz.siret.prank.prediction.pockets.clustering

import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic

/**
 * Strategy for clustering points with a score into pockets.
 */
@CompileStatic
abstract class ClusteringStrategy {

    abstract List<Atoms> clusterPointsIntoPockets(List<LabeledPoint> points)


    static ClusteringStrategy create(String strategyName) {
        switch (strategyName) {
            case "SingleLinkage":
                return new SingleLinkageClustering()
            case "ZScore":
                return new ZScoreSingleLinkageClustering()
            default:
                throw new IllegalArgumentException("Unknown clustering strategy: $strategyName")
        }
    }

}
