package cz.siret.prank.score.prediction

import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import cz.siret.prank.domain.Pocket
import cz.siret.prank.geom.Atoms

@CompileStatic
class PrankPocket extends Pocket {

    Atoms innerPoints

    PrankPocket(Atom centroid, double score, Atoms innerPoints) {
        this.centroid = centroid
        this.newScore = score
        this.innerPoints = innerPoints
    }

}
