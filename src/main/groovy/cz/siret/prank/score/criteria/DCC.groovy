package cz.siret.prank.score.criteria

import groovy.transform.CompileStatic
import cz.siret.prank.domain.Ligand
import cz.siret.prank.domain.Pocket
import cz.siret.prank.geom.Struct

/**
 * distance from the center of the pocket to the center of the ligand
 */
@CompileStatic
class DCC implements IdentificationCriterium {

    final double cutoff

    DCC(double cutoff) {
        this.cutoff = cutoff
    }

    @Override
    boolean isIdentified(Ligand ligand, Pocket pocket) {

        return cutoff >= Struct.dist(ligand.centroid, pocket.centroid)
    }

    @Override
    double score(Ligand ligand, Pocket pocket) {
        return cutoff - Struct.dist(ligand.centroid, pocket.centroid)
    }


    @Override
    String toString() {
        "DCC($cutoff)"
    }

}
