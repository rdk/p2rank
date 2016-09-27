package cz.siret.prank.geom.clustering

import groovy.transform.CompileStatic

/**
 * Generic element clusterer.
 */
@CompileStatic
abstract interface Clusterer<E> {

    interface Distance<T> {
        double dist(T a, T b)
    }

    abstract List<List<E>> cluster(List<E> elements, double minDist, Distance<E> distDef)

}
