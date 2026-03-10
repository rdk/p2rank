package cz.siret.prank.prediction.pockets.criteria

import cz.siret.prank.domain.BindingSite
import cz.siret.prank.domain.Pocket
import cz.siret.prank.geom.Struct
import org.biojava.nbio.structure.Atom
import cz.siret.prank.program.routines.results.EvalContext
import groovy.transform.CompileStatic

/**
 * distance from the center of the pocket to the center of the ligand
 */
@CompileStatic
class DCC extends PocketCriterion {

    final double cutoff

    DCC(String name, double cutoff) {
        super(name)
        this.cutoff = cutoff
    }

    @Override
    boolean isIdentified(BindingSite site, Pocket pocket, EvalContext context) {
        Atom siteCenter = site.centerForEval
        if (siteCenter == null || pocket.centroid == null) {
            return false
        }
        return cutoff >= Struct.dist(siteCenter, pocket.centroid)
    }

    @Override
    double score(BindingSite site, Pocket pocket) {
        Atom siteCenter = site.centerForEval
        if (siteCenter == null || pocket.centroid == null) {
            return Double.NEGATIVE_INFINITY
        }
        return cutoff - Struct.dist(siteCenter, pocket.centroid)
    }

    @Override
    String toString() {
        "DCC($cutoff)"
    }

}
