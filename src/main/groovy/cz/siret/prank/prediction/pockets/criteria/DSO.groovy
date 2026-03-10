package cz.siret.prank.prediction.pockets.criteria

import cz.siret.prank.domain.BindingSite
import cz.siret.prank.domain.Pocket
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.routines.results.EvalContext
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.util.function.Function

/**
 * Discretized surface overlap ratio (similar to DeepSite DVO criterion).
 * Defined as Jaccard/Tanimoto coefficient of SAS points of the binding site and pocket:
 * |intersection| / |union| of SAS points induced by the site and defined by the pocket.
 */
@Slf4j
@CompileStatic
class DSO extends PocketCriterion {

    final double threshold

    DSO(String name, double threshold) {
        super(name)
        this.threshold = threshold
    }

    static Tuple2<Atoms, Atoms> getUnionAndIntersection(BindingSite site, Pocket pocket, EvalContext context) {
        def cache = (Map<Tuple2<BindingSite, Pocket>, Tuple2<Atoms, Atoms>>) context.cache.get('sas_set_cache', new HashMap())

        def key = new Tuple2(site, pocket)

        def sets = cache.computeIfAbsent(key, new Function<Tuple2<BindingSite, Pocket>, Tuple2<Atoms, Atoms>>() {
            @Override
            Tuple2<Atoms, Atoms> apply(Tuple2<BindingSite, Pocket> t) {
                Atoms union =  Atoms.union(site.sasPoints, pocket.sasPoints)
                Atoms inter = (union.empty) ? new Atoms() : Atoms.intersection(site.sasPoints, pocket.sasPoints)
                new Tuple2(union, inter)
            }
        })
        sets
    }

    @Override
    boolean isIdentified(BindingSite site, Pocket pocket, EvalContext context) {
        if (pocket.sasPoints == null) { // pocket does not define sas points
            return false
        }

        def sets = getUnionAndIntersection(site, pocket, context)
        int union = sets.first.count
        int inter = sets.second.count

        if (inter==0 || union==0)
            return false

        double ratio = (double) inter / union

        return ratio >= threshold
    }

    @Override
    double score(BindingSite site, Pocket pocket) {
        // TODO return cached ratio
        return Double.NaN
    }

    @Override
    String toString() {
        "DSO($threshold)"
    }

}
