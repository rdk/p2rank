package cz.siret.prank.score.criteria

import cz.siret.prank.domain.Ligand
import cz.siret.prank.domain.Pocket
import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic

/**
 * discretized surface weighted overlap
 *
 * given threshols from <0,1>
 *
 * pocket is correctly predicted iff:
 *  at least ligandCoverageThreshold of the ligand is covered by the pocket and
 *  at least pocketCoverageThreshold of the pocket is covered by the ligand
 *
 */
@CompileStatic
class DSWO implements IdentificationCriterium {

    final double ligandCoverageThreshold
    final double pocketCoverageThreshold

    DSWO(double ligandCoverageThreshold, double pocketCoverageThreshold) {
        this.ligandCoverageThreshold = ligandCoverageThreshold
        this.pocketCoverageThreshold = pocketCoverageThreshold
    }

    @Override
    boolean isIdentified(Ligand ligand, Pocket pocket) {
        if (pocket.sasPoints == null) { // pocket does not define sas points
            return false
        }

        int inter = Atoms.intersection(ligand.sasPoints, pocket.sasPoints).count
        if (inter==0)
            return false

        int nlig = ligand.sasPoints.count
        int npoc = pocket.sasPoints.count

        double ligCov = inter / nlig
        double pocCov = inter / npoc

        return (ligCov >= ligandCoverageThreshold) && (pocCov >= pocketCoverageThreshold)
    }

    @Override
    double score(Ligand ligand, Pocket pocket) {
        return Double.NaN
    }

    @Override
    String toString() {
        "DSWO($ligandCoverageThreshold;$pocketCoverageThreshold)"
    }

}