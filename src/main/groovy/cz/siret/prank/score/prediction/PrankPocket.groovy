package cz.siret.prank.score.prediction

import cz.siret.prank.domain.Pocket
import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

@CompileStatic
class PrankPocket extends Pocket {

    Atoms innerPoints

    PrankPocket(Atom centroid, double score, Atoms innerPoints) {
        this.centroid = centroid
        this.newScore = score
        this.innerPoints = innerPoints
    }

}
