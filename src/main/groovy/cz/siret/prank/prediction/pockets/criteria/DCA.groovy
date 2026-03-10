package cz.siret.prank.prediction.pockets.criteria

import cz.siret.prank.domain.BindingSite
import cz.siret.prank.domain.Pocket
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.routines.results.EvalContext
import groovy.transform.CompileStatic

/**
 * distance from the center of the predicted pocket to any atom of the ligand
 */
@CompileStatic
class DCA extends PocketCriterion implements Parametrized {

    final double cutoff

    DCA(String name, double cutoff) {
        super(name)
        this.cutoff = cutoff
    }

    private Atoms getSitePoints(BindingSite site) {
        return params.site_eval_sas_pts_as_atoms ? site.sasPoints : site.atoms
    }

    @Override
    boolean isIdentified(BindingSite site, Pocket pocket, EvalContext context) {
        return getSitePoints(site).areWithinDistance(pocket.centroid, cutoff)
    }

    @Override
    double score(BindingSite site, Pocket pocket) {
        return cutoff - getSitePoints(site).dist(pocket.centroid)
    }

    @Override
    String toString() {
        "DCA($cutoff)"
    }

}
