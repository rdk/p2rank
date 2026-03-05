package cz.siret.prank.prediction.pockets.criteria

import cz.siret.prank.domain.BindingSite
import cz.siret.prank.domain.Pocket
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.routines.results.EvalContext
import groovy.transform.CompileStatic

/**
 * distance from the center of the pocket to the center of the ligand
 */
@CompileStatic
class DCC extends PocketCriterium {

    final double cutoff

    DCC(String name, double cutoff) {
        super(name)
        this.cutoff = cutoff
    }

    // Uses BindingSite.getCentroid():
    //   Ligand:      atoms.centerOfMass (mass-weighted center)
    //   ResidueSite: predefined centroid from input file

    @Override
    boolean isIdentified(BindingSite site, Pocket pocket, EvalContext context) {
        if (site.centroidForEval == null || pocket.centroid == null) return false
        return cutoff >= Struct.dist(site.centroidForEval, pocket.centroid)
    }

    @Override
    double score(BindingSite site, Pocket pocket) {
        if (site.centroidForEval == null || pocket.centroid == null) return Double.NEGATIVE_INFINITY
        return cutoff - Struct.dist(site.centroidForEval, pocket.centroid)
    }

    @Override
    String toString() {
        "DCC($cutoff)"
    }

}
