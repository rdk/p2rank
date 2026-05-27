package cz.siret.prank.domain

import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

@CompileStatic
abstract class Pocket {

    String name = "pocket"
    Atoms surfaceAtoms = new Atoms()
    Atom centroid
    Atoms sasPoints = null
    List<LabeledPoint> labeledPoints = null  // labeled SAS points

    /**
     * Algorithm rank (1-based). In predict mode, position in the score-sorted
     * unfiltered list. In rescore mode, original rank from the external method.
     * Set on ALL pockets (including filtered-out ones) before output filtering.
     */
    int rank

    double score = Double.NaN

    /**
     * Output rank (1-based). Position in {@code prediction.outputPockets}
     * after filtering and finalization. Only meaningful on pockets that
     * survived filtering; filtered-out pockets retain the pre-filter value.
     * Eval derives rank from list position, not from this field.
     */
    int newRank
    double newScore

    private List<Residue> residues = null

    PocketStats stats = new PocketStats()
    AuxInfo auxInfo = new AuxInfo()
    Map<String, Object> cache = new HashMap<>() // cache for various data

    /**
     * SAS points defined by the pocket.
     * By default returns null. Defined only for some pocket types (PrankPocket, FpocketPocket).
     */
    Atoms getSasPoints() {
        return sasPoints
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

    List<Residue> getResidues() {
        if (residues==null) {
            if (surfaceAtoms==null || surfaceAtoms.empty) {
                residues = Collections.emptyList()
            } else {
                residues = surfaceAtoms.distinctGroupsSorted.collect { new Residue(it) }.toList()
            }
        }
        residues
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
