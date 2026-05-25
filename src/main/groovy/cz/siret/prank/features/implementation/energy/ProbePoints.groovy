package cz.siret.prank.features.implementation.energy

import cz.siret.prank.domain.labeling.LabeledPoint
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

    /**
     * Extract per-point energy scores from a cloud as a primitive {@code double[]}.
     * Replaces the hot-path Groovy closure {@code cloudPoints.collect { ... } as List<Double>}
     * which boxed every score and resolved through Groovy's invokedynamic call-site
     * cache (see commit 38fcff63 for the equivalent fix in geom/Atoms).
     */
    static double[] extractScores(Atoms cloudPoints) {
        int n = cloudPoints.count
        double[] arr = new double[n]
        for (int i = 0; i < n; i++) {
            arr[i] = ((LabeledPoint) cloudPoints.list.get(i)).score
        }
        return arr
    }
}
