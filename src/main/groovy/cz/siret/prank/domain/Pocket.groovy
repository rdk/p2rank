package cz.siret.prank.domain

import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

@CompileStatic
abstract class Pocket {

    String name = "pocket"
    Atoms surfaceAtoms = new Atoms()
    Atom centroid

    /**
     * original rank of predicted pocket, starting with 1
     */
    int rank

    double score = Double.NaN

    /**
     * rank of pocket after rescoring
     */
    int newRank
    double newScore

    PocketStats stats = new PocketStats()
    AuxInfo auxInfo = new AuxInfo()
    Map<String, Object> cache = new HashMap<>() // cache for various data

    /**
     * SAS points defined by the pocket.
     * By default returns null. Defined only for some pocket types (PrankPocket, FpocketPocket).
     */
    Atoms getSasPoints() {
        return null
    }

    @Override
    String toString() {
        return "pocket rank:$rank surfaceAtoms:${surfaceAtoms.count}"
    }

    Atom getCentroid() {
        return centroid
    }

    void setCentroid(Atom centroid) {
        this.centroid = centroid
    }

    static class AuxInfo {
        int samplePoints
        double rawNewScore
        double zScoreTP
        double probaTP
    }

    static class PocketStats {
        double pocketScore
        double realVolumeApprox
    }

}
