package cz.siret.prank.score.criteria

import cz.siret.prank.domain.Ligand
import cz.siret.prank.domain.Pocket
import groovy.transform.CompileStatic

/**
 * distance from any of the pocket surface atoms to any atom of the ligand
 */
@CompileStatic
class DSA implements IdentificationCriterium {

    final double cutoff

    DSA(double cutoff) {
        this.cutoff = cutoff
    }

    @Override
    boolean isIdentified(Ligand ligand, Pocket pocket) {

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
