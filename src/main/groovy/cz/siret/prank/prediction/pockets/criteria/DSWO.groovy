package cz.siret.prank.prediction.pockets.criteria

import cz.siret.prank.domain.BindingSite
import cz.siret.prank.domain.Pocket
import cz.siret.prank.program.routines.results.EvalContext
import groovy.transform.CompileStatic

/**
 * Discretized surface weighted overlap.
 *
 * Pocket is correctly predicted iff:
 *  at least ligandCoverageThreshold of the ligand SAS points are covered by the pocket, and
 *  at least pocketCoverageThreshold of the pocket SAS points are covered by the ligand.
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
        if (pocket.sasPoints == null) {
            return false
        }

        DSO.OverlapCounts counts = DSO.getOverlapCounts(site, pocket, context)

        if (counts.intersectionCount == 0) {
            return false
        }

        int siteSasCount = site.sasPoints.count
        int pocketSasCount = pocket.sasPoints.count
        if (siteSasCount == 0 || pocketSasCount == 0) {
            return false
        }

        double ligCov = (double) counts.intersectionCount / siteSasCount
        double pocCov = (double) counts.intersectionCount / pocketSasCount

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
