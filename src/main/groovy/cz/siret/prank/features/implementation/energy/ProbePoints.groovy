package cz.siret.prank.features.implementation.energy

import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic

/**
 *
 */
@CompileStatic
class ProbePoints {

    Atoms points // elements are LabeledPoint

    ProbePoints(Atoms points) {
        this.points = points
    }

}
