package cz.siret.prank.prediction.pockets

import cz.siret.prank.domain.Pocket
import cz.siret.prank.geom.Atoms
import cz.siret.prank.domain.labeling.LabeledPoint
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

@CompileStatic
class PrankPocket extends Pocket {

    Atoms sasPoints
    List<LabeledPoint> labeledPoints

    PrankPocket(Atom centroid, double score, Atoms sasPoints, List<LabeledPoint> labeledPoints) {
        this.centroid = centroid
        this.score = score
        this.newScore = score
        this.sasPoints = sasPoints
        this.labeledPoints = labeledPoints
    }

    @Override
    Atoms getSasPoints() {
        return sasPoints
    }
    
}
