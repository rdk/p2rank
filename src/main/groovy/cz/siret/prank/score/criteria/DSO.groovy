package cz.siret.prank.score.criteria

import cz.siret.prank.domain.Ligand
import cz.siret.prank.domain.Pocket
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.routines.results.EvalContext
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.util.function.Function

/**
 * discretized surface overlap ratio (similar to DeepSite DVO criterion)
 * |intersection|/|union| of SAS points induced by ligand and defined by pocket
 *
 * TODO unfifished
 */
@Slf4j
@CompileStatic
class DSO implements IdentificationCriterium {

    final double threshold

    DSO(double threshold) {
        this.threshold = threshold
    }

    static Tuple2<Atoms, Atoms> getUnionAndIntersection(Ligand ligand, Pocket pocket, EvalContext context) {
        def cahe = (Map<Tuple2<Ligand, Pocket>, Tuple2<Atoms, Atoms>>) context.cache.get('sas_set_cache', new HashMap())

        def key = new Tuple2(ligand, pocket)
        def sets = cahe.computeIfAbsent(key, new Function<Tuple2<Ligand, Pocket>, Tuple2<Atoms, Atoms>>() {
            @Override
            Tuple2<Atoms, Atoms> apply(Tuple2<Ligand, Pocket> tuple2) {
                Atoms union =  Atoms.union(ligand.sasPoints, pocket.sasPoints)
                Atoms inter = (union.empty) ? new Atoms() : Atoms.intersection(ligand.sasPoints, pocket.sasPoints)
                new Tuple2(union, inter)
            }
        })
        sets
    }

    @Override
    boolean isIdentified(Ligand ligand, Pocket pocket, EvalContext context) {
        if (pocket.sasPoints == null) { // pocket does not define sas points
            return false
        }

        def sets = getUnionAndIntersection(ligand, pocket, context)
        int union = sets.first.count
        int inter = sets.second.count

        log.warn("I:$inter")
        if (inter==0)
            return false
        log.warn("U:$inter")
        if (union==0)
            return false


        double ratio = inter / union

        return ratio >= threshold
    }

    @Override
    double score(Ligand ligand, Pocket pocket) {
        return Double.NaN
    }

    @Override
    String toString() {
        "DSO($threshold)"
    }

}