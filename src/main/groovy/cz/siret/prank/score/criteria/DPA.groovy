package cz.siret.prank.score.criteria

import cz.siret.prank.domain.Ligand
import cz.siret.prank.domain.Pocket
import cz.siret.prank.score.prediction.PrankPocket
import groovy.transform.CompileStatic

/**
 * distance from any of the pocket connolly points to any atom of the ligand
 */
@CompileStatic
class DPA implements IdentificationCriterium {

    final double cutoff

    DPA(double cutoff) {
        this.cutoff = cutoff
    }

    @Override
    boolean isIdentified(Ligand ligand, Pocket pocket) {

        if (!(pocket instanceof PrankPocket)) return false
        PrankPocket pp = (PrankPocket) pocket

        return ligand.atoms.areWithinDistance(pp.innerPoints, cutoff)
    }

    @Override
    double score(Ligand ligand, Pocket pocket) {

        if (!(pocket instanceof PrankPocket)) return 0
        PrankPocket pp = (PrankPocket) pocket

        return cutoff - ligand.atoms.dist(pp.innerPoints)
    }

    @Override
    String toString() {
        "DPA($cutoff)"
    }

}
