package cz.siret.prank.prediction.pockets.criteria

import cz.siret.prank.domain.Ligand
import cz.siret.prank.domain.Pocket
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.routines.results.EvalContext
import groovy.transform.CompileStatic

/**
 * distance from the center of the pocket to the center of the ligand
 */
@CompileStatic
class DCC implements PocketCriterium {

    final double cutoff

    DCC(double cutoff) {
        this.cutoff = cutoff
    }

    @Override
    boolean isIdentified(Ligand ligand, Pocket pocket, EvalContext context) {

//        return cutoff >= Struct.dist(ligand.centroid, pocket.centroid)
        return cutoff >= Struct.dist(ligand.atoms.toPoints().centerOfMass, pocket.centroid)
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
