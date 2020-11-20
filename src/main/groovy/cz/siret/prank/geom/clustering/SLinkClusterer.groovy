package cz.siret.prank.geom.clustering

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 *  Singe linkage clusterer.
 *
 *  TOTO: this is O(n^3), implement SLink algorithm
 */
@Slf4j
@CompileStatic
class SLinkClusterer<E> implements Clusterer<E> {

    @CompileStatic
    private static class Cluster<E> {
        List<E> list
        int id

        Cluster(E newElement, int id) {
            list = new LinkedList()
            list.add(newElement)
            this.id = id
        }

        int getSize() {
            list.size()
        }

        boolean equals(o) {
            if (this.is(o)) return true
            Cluster cluster = (Cluster) o
            if (id != cluster.id) return false
            return true
        }

        int hashCode() {
            return id
        }

        @Override
        public String toString() {
            return "id:$id( $size )"
        }
    }

    int getAndFixPath(int i, int[] lambda) {
        if (lambda[i] == i) {
            return i
        } else {
            lambda[i] = getAndFixPath(lambda[i], lambda)
            return lambda[i]
        }
    }

    @Override
    List<List<E>> cluster(List<E> elements, double minDist, Clusterer.Distance<E> distDef) {

        if (elements.empty) return Collections.emptyList()
        if (elements.size()==1) return new ArrayList<List<E>>([elements])

        E[] els = elements.toArray() as E[]
        int N = els.length

        Cluster<E>[] clust = new Cluster[N]
        for (int i=0; i<N; i++) {
            clust[i] = new Cluster<E>(els[i], i)
        }
        int[] lambda = new int[N] // point_id -> higher cluster id
        for (int i=0; i<N; i++) {
            lambda[i] = i
        }

        log.info "clustering [$N] elements"

        for (int j=N-1; j>=1; j--) {
            for (int i=j-1; i>=0; i--) {                              // for every element pair

//                int clj = getAndFixPath(j, lambda)
//                int cli = getAndFixPath(i, lambda)

                int cli = lambda[i]
                int clj = lambda[j]

                if (cli != clj) {                      // from different clusters
                    double dist = distDef.dist(els[i],els[j])



                    if (dist<=minDist) {                           // if clse enough
                        //log.info "JOINING CLUSTERS $i: ${clust[cli]} + $j: ${clust[clj]}"

                        // i<j
                        clust[clj].list.addAll(clust[cli].list)        // join clusters

                        for (int k=0; k<N; k++) {    //relabel
                            if (lambda[k]==cli) {
                                lambda[k] = clj
                            }
                        }
                    }
                }

            }
        }

        Set<Cluster> distinctClust = new HashSet<Cluster>(N)
        for (int i=0; i<N; i++) {
            distinctClust.add(clust[getAndFixPath(i, lambda)])
        }

        log.info "clusters: " + distinctClust.toListString()
        log.info "clusters together: " + distinctClust*.size.sum(0) + " / " + elements.size()

        return (distinctClust*.list).toList() as List<List<E>>
    }

}
