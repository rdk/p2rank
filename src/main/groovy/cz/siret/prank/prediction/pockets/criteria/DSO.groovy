package cz.siret.prank.prediction.pockets.criteria

import cz.siret.prank.domain.BindingSite
import cz.siret.prank.domain.Pocket
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.routines.results.EvalContext
import groovy.transform.CompileStatic

/**
 * Discretized surface overlap ratio (similar to DeepSite DVO criterion).
 * Defined as Jaccard/Tanimoto coefficient of SAS points of the binding site and pocket:
 * |intersection| / |union| of SAS points induced by the site and defined by the pocket.
 */
@CompileStatic
class DSO extends PocketCriterion {

    final double threshold

    DSO(String name, double threshold) {
        super(name)
        this.threshold = threshold
    }

    /**
     * Cached overlap counts for a (site, pocket) pair.
     */
    static class OverlapCounts {
        final int intersectionCount
        final int unionCount

        OverlapCounts(int intersectionCount, int unionCount) {
            this.intersectionCount = intersectionCount
            this.unionCount = unionCount
        }

        double ratio() {
            unionCount == 0 ? 0d : (double) intersectionCount / unionCount
        }
    }

    /**
     * Cache key for a (site, pocket) pair, using identity equality.
     */
    private static class SitePocketKey {
        final BindingSite site
        final Pocket pocket

        SitePocketKey(BindingSite site, Pocket pocket) {
            this.site = site
            this.pocket = pocket
        }

        @Override
        boolean equals(Object o) {
            if (!(o instanceof SitePocketKey)) return false
            SitePocketKey other = (SitePocketKey) o
            return site.is(other.site) && pocket.is(other.pocket)
        }

        @Override
        int hashCode() {
            return System.identityHashCode(site) * 31 + System.identityHashCode(pocket)
        }
    }

    /**
     * Returns cached overlap counts for the given site-pocket pair.
     */
    static OverlapCounts getOverlapCounts(BindingSite site, Pocket pocket, EvalContext context) {
        Map<SitePocketKey, OverlapCounts> cache = (Map<SitePocketKey, OverlapCounts>) context.cache.computeIfAbsent(
                'sas_overlap_cache', { new HashMap<>() })

        return cache.computeIfAbsent(new SitePocketKey(site, pocket), {
            int inter = Atoms.intersection(site.sasPoints, pocket.sasPoints).count
            int union = Atoms.union(site.sasPoints, pocket.sasPoints).count
            new OverlapCounts(inter, union)
        })
    }

    @Override
    boolean isIdentified(BindingSite site, Pocket pocket, EvalContext context) {
        if (pocket.sasPoints == null) {
            return false
        }

        OverlapCounts counts = getOverlapCounts(site, pocket, context)
        return counts.intersectionCount > 0 && counts.ratio() >= threshold
    }

    @Override
    double score(BindingSite site, Pocket pocket) {
        return Double.NaN  // score not used yet
    }

    @Override
    String toString() {
        "DSO($threshold)"
    }

}
