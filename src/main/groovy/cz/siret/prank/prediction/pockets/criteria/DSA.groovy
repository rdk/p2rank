package cz.siret.prank.prediction.pockets.criteria

import cz.siret.prank.domain.Ligand
import cz.siret.prank.domain.Pocket
import cz.siret.prank.program.routines.results.EvalContext
import groovy.transform.CompileStatic

/**
 * distance from any of the pocket surface atoms to any atom of the ligand
 */
@CompileStatic
class DSA extends PocketCriterium {

    final double cutoff

    DSA(String name, double cutoff) {
        super(name)
        this.cutoff = cutoff
    }

    @Override
    boolean isIdentified(Ligand ligand, Pocket pocket, EvalContext context) {

        return ligand.atoms.areWithinDistance(pocket.surfaceAtoms, cutoff)
    }

    @Override
    double score(Ligand ligand, Pocket pocket) {

        return cutoff - ligand.atoms.dist(pocket.surfaceAtoms)
    }

    @Override
    String toString() {
        "DSA($cutoff)"
    }

}
