package cz.siret.prank.domain.labeling

import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic

/**
 *
 */
@CompileStatic
abstract class PointLabeler {

    abstract List<LabeledPoint> labelPoints(Atoms points, Protein protein)

}
