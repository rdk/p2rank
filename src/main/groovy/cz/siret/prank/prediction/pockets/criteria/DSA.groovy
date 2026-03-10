package cz.siret.prank.prediction.pockets.criteria

import cz.siret.prank.domain.BindingSite
import cz.siret.prank.domain.Pocket
import cz.siret.prank.program.routines.results.EvalContext
import groovy.transform.CompileStatic

/**
 * distance from any of the pocket surface atoms to any atom of the ligand
 */
@CompileStatic
class DSA extends PocketCriterion {

    final double cutoff

    DSA(String name, double cutoff) {
        super(name)
        this.cutoff = cutoff
    }

    @Override
    boolean isIdentified(BindingSite site, Pocket pocket, EvalContext context) {

        return site.atoms.areWithinDistance(pocket.surfaceAtoms, cutoff)
    }

    @Override
    double score(BindingSite site, Pocket pocket) {

        return cutoff - site.atoms.dist(pocket.surfaceAtoms)
    }

    @Override
    String toString() {
        "DSA($cutoff)"
    }

}
