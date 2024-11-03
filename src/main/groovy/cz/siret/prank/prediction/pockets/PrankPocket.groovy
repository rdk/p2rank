package cz.siret.prank.prediction.pockets

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

@CompileStatic
class PrankPocket extends Pocket {

    PrankPocket(Atom centroid, double score, Atoms sasPoints, List<LabeledPoint> labeledPoints) {
        this.centroid = centroid
        this.score = score
        this.newScore = score
        this.sasPoints = sasPoints
        this.labeledPoints = labeledPoints
    }

}
