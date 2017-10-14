package cz.siret.prank.score.prediction

import cz.siret.prank.domain.Pocket
import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

@CompileStatic
class PrankPocket extends Pocket {

    Atoms sasPoints

    PrankPocket(Atom centroid, double score, Atoms sasPoints) {
        this.centroid = centroid
        this.score = score
        this.newScore = score
        this.sasPoints = sasPoints
    }

    @Override
    Atoms getSasPoints() {
        return sasPoints
    }
    
}
