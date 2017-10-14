package cz.siret.prank.score.criteria

import cz.siret.prank.domain.Ligand
import cz.siret.prank.domain.Pocket
import groovy.transform.CompileStatic

/**
 * discretized surface overlap ratio
 * |intersection|/|union| of SAS points induced by ligand and defined by pocket
 *
 * TODO unfifished
 */
@CompileStatic
class DSO implements IdentificationCriterium {

    final double threshold

    DSO(double threshold) {
        this.threshold = threshold
    }

    @Override
    boolean isIdentified(Ligand ligand, Pocket pocket) {

        return ligand.atoms.areWithinDistance(pocket.centroid, threshold)
    }

    @Override
    double score(Ligand ligand, Pocket pocket) {
        return threshold - ligand.atoms.dist(pocket.centroid)
    }

    @Override
    String toString() {
        "DCA($threshold)"
    }

}