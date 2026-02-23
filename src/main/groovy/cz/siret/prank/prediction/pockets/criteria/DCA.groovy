package cz.siret.prank.prediction.pockets.criteria

import cz.siret.prank.domain.BindingSite
import cz.siret.prank.domain.Pocket
import cz.siret.prank.program.routines.results.EvalContext
import groovy.transform.CompileStatic

/**
 * distance from the center of the predicted pocket to any atom of the ligand
 */
@CompileStatic
class DCA extends PocketCriterium {

    final double cutoff

    DCA(String name, double cutoff) {
        super(name)
        this.cutoff = cutoff
    }

    @Override
    boolean isIdentified(BindingSite site, Pocket pocket, EvalContext context) {

        return site.atoms.areWithinDistance(pocket.centroid, cutoff)
    }

    @Override
    double score(BindingSite site, Pocket pocket) {
        return cutoff - site.atoms.dist(pocket.centroid)
    }

    @Override
    String toString() {
        "DCA($cutoff)"
    }

}
