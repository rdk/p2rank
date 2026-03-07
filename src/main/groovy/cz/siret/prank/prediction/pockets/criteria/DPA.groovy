package cz.siret.prank.prediction.pockets.criteria

import cz.siret.prank.domain.BindingSite
import cz.siret.prank.domain.Pocket
import cz.siret.prank.prediction.pockets.PrankPocket
import cz.siret.prank.program.routines.results.EvalContext
import groovy.transform.CompileStatic

/**
 * distance from any of the pocket SAS points to any atom of the ligand
 */
@CompileStatic
class DPA extends PocketCriterium {

    final double cutoff

    DPA(String name, double cutoff) {
        super(name)
        this.cutoff = cutoff
    }

    @Override
    boolean isIdentified(BindingSite site, Pocket pocket, EvalContext context) {

        if (!(pocket instanceof PrankPocket)) return false
        PrankPocket pp = (PrankPocket) pocket

        return site.ligandAtoms.areWithinDistance(pp.sasPoints, cutoff)
    }

    @Override
    double score(BindingSite site, Pocket pocket) {

        if (!(pocket instanceof PrankPocket)) return 0
        PrankPocket pp = (PrankPocket) pocket

        return cutoff - site.ligandAtoms.dist(pp.sasPoints)
    }

    @Override
    String toString() {
        "DPA($cutoff)"
    }

}
