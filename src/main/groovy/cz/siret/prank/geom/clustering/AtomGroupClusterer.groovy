package cz.siret.prank.geom.clustering

import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic

/**
 *
 */
@CompileStatic
class AtomGroupClusterer implements Clusterer<Atoms> {

    static final Clusterer.Distance<Atoms> EUCLID_DIST = new EuclidDist()

    Clusterer<Atoms> clusteringAlgorithm

    AtomGroupClusterer(Clusterer clusteringAlgorithm) {
        this.clusteringAlgorithm = clusteringAlgorithm
    }

    static AtomGroupClusterer singleLinkage() {
        return new AtomGroupClusterer(new SLinkClustererV2<Atoms>())
    }

    @Override
    List<List<Atoms>> cluster(List<Atoms> elements, double minDist, Clusterer.Distance<Atoms> distDef) {
        return clusteringAlgorithm.cluster(elements, minDist, distDef)
    }

    List<Atoms> clusterGroups(List<Atoms> elements, double minDist) {
        cluster(elements, minDist, EUCLID_DIST).collect { list -> Atoms.join(list) }.toList()
    }

//===========================================================================================================//

    static class EuclidDist implements Clusterer.Distance<Atoms> {
        @Override
        double dist(Atoms a, Atoms b) {
            if (a==null) return Double.POSITIVE_INFINITY

            return a.dist(b)
        }
    }

}
