package cz.siret.prank.prediction.pockets.criteria

import cz.siret.prank.domain.Ligand
import cz.siret.prank.domain.Pocket
import cz.siret.prank.program.routines.results.EvalContext
import groovy.transform.CompileStatic

/**
 * distance from the center of the predicted pocket to any atom of the ligand
 */
@CompileStatic
class DCA implements PocketCriterium {

    final double cutoff

    DCA(double cutoff) {
        this.cutoff = cutoff
    }

    @Override
    boolean isIdentified(Ligand ligand, Pocket pocket, EvalContext context) {

        return ligand.atoms.areWithinDistance(pocket.centroid, cutoff)
    }

    @Override
    double score(Ligand ligand, Pocket pocket) {
        return cutoff - ligand.atoms.dist(pocket.centroid)
    }

    @Override
    String toString() {
        "DCA($cutoff)"
    }

}
