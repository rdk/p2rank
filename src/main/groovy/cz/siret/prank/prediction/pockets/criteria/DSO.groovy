package cz.siret.prank.prediction.pockets.criteria

import cz.siret.prank.domain.BindingSite
import cz.siret.prank.domain.Pocket
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.routines.results.EvalContext
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.util.function.Function

/**
 * discretized surface overlap ratio (similar to DeepSite DVO criterion)
 * Defined as Jaccard/Tanimoto coefficient of SAS points of ligand and pocket.
 *
 * |intersection|/|union| of SAS points induced by ligand and defined by pocket
 *
 * TODO unfinished
 */
@Slf4j
@CompileStatic
class DSO extends PocketCriterium {

    final double threshold

    DSO(String name, double threshold) {
        super(name)
        this.threshold = threshold
    }

    static Tuple2<Atoms, Atoms> getUnionAndIntersection(BindingSite site, Pocket pocket, EvalContext context) {
        def cahe = (Map<Tuple2<BindingSite, Pocket>, Tuple2<Atoms, Atoms>>) context.cache.get('sas_set_cache', new HashMap())

        def key = new Tuple2(site, pocket)

        def sets = cahe.computeIfAbsent(key, new Function<Tuple2<BindingSite, Pocket>, Tuple2<Atoms, Atoms>>() {
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

//        log.warn("I:$inter")
        if (inter==0)
            return false
//        log.warn("U:$union")
        if (union==0)
            return false


        double ratio = inter / union

        return ratio >= threshold
    }

    @Override
    double score(BindingSite site, Pocket pocket) {
        return Double.NaN
    }

    @Override
    String toString() {
        "DSO($threshold)"
    }

}
