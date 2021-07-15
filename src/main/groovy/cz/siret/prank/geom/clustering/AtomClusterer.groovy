package cz.siret.prank.geom.clustering

import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Clusterer wrapper for Atoms with euclidean distance.
 */
@CompileStatic
class AtomClusterer implements Clusterer<Atom> {

    static final Clusterer.Distance<Atom> SQR_EUCLID = new SqrEuclidDist()

    Clusterer<Atom> clusteringAlgorithm

    AtomClusterer(Clusterer clusteringAlgorithm) {
        this.clusteringAlgorithm = clusteringAlgorithm
    }

    @Override
    List<List<Atom>> cluster(List<Atom> elements, double minDist, Clusterer.Distance<Atom> distDef) {
        return clusteringAlgorithm.cluster(elements, minDist, distDef)
    }

    List<Atoms> clusterAtoms(Atoms atoms, double minDist) {
        double sqrDist = minDist*minDist
        return cluster(atoms.list, sqrDist, SQR_EUCLID).collect {new Atoms(it)}.toList()
    }

//===========================================================================================================//

    static class SqrEuclidDist implements Clusterer.Distance<Atom> {
        @Override
        double dist(Atom a, Atom b) {
            Struct.sqrDist(a,b)
        }
    }

}
