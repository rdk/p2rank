package cz.siret.prank.prediction.pockets.criteria

import cz.siret.prank.domain.BindingSite
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
class DSWO extends PocketCriterion {

    final double ligandCoverageThreshold
    final double pocketCoverageThreshold

    DSWO(String name, double ligandCoverageThreshold, double pocketCoverageThreshold) {
        super(name)
        this.ligandCoverageThreshold = ligandCoverageThreshold
        this.pocketCoverageThreshold = pocketCoverageThreshold
    }

    @Override
    boolean isIdentified(BindingSite site, Pocket pocket, EvalContext context) {
        if (pocket.sasPoints == null) { // pocket does not define sas points
            return false
        }

        def sets = DSO.getUnionAndIntersection(site, pocket, context)
        int inter = sets.second.count

        if (inter==0)
            return false

        int nlig = site.sasPoints.count
        int npoc = pocket.sasPoints.count

        double ligCov = inter / nlig
        double pocCov = inter / npoc

        return (ligCov >= ligandCoverageThreshold) && (pocCov >= pocketCoverageThreshold)
    }

    @Override
    double score(BindingSite site, Pocket pocket) {
        return Double.NaN
    }

    @Override
    String toString() {
        "DSWO($ligandCoverageThreshold;$pocketCoverageThreshold)"
    }

}
