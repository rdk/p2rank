package cz.siret.prank.prediction.pockets.criteria

import cz.siret.prank.domain.Ligand
import cz.siret.prank.domain.Pocket
import cz.siret.prank.program.routines.results.EvalContext
import groovy.transform.CompileStatic

/**
 * discretized surface weighted overlap
 *
 * given thresholds from <0,1>
 *
 * pocket is correctly predicted iff:
 *  at least ligandCoverageThreshold of the ligand is covered by the pocket and
 *  at least pocketCoverageThreshold of the pocket is covered by the ligand
 *
 */
@CompileStatic
class DSWO implements PocketCriterium {

    final double ligandCoverageThreshold
    final double pocketCoverageThreshold

    DSWO(double ligandCoverageThreshold, double pocketCoverageThreshold) {
        this.ligandCoverageThreshold = ligandCoverageThreshold
        this.pocketCoverageThreshold = pocketCoverageThreshold
    }

    @Override
    boolean isIdentified(Ligand ligand, Pocket pocket, EvalContext context) {
        if (pocket.sasPoints == null) { // pocket does not define sas points
            return false
        }

        def sets = DSO.getUnionAndIntersection(ligand, pocket, context)
        int inter = sets.second.count

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