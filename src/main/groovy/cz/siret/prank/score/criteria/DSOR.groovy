package cz.siret.prank.score.criteria

import cz.siret.prank.domain.Ligand
import cz.siret.prank.domain.Pocket
import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * discretized surface overlap ratio (similar to DeepSite DVO criterion)
 * |intersection|/|union| of SAS points induced by ligand and defined by pocket
 *
 * TODO unfifished
 */
@Slf4j
@CompileStatic
class DSOR implements IdentificationCriterium {

    final double threshold

    DSOR(double threshold) {
        this.threshold = threshold
    }

    @Override
    boolean isIdentified(Ligand ligand, Pocket pocket) {
        if (pocket.sasPoints == null) { // pocket does not define sas points
            return false
        }

//        log.warn("L:$ligand.sasPoints.count P:$pocket.sasPoints.count")

        int inter = Atoms.intersection(ligand.sasPoints, pocket.sasPoints).count
//        log.warn("I:$inter")
        if (inter==0)
            return false
        int union = Atoms.union(ligand.sasPoints, pocket.sasPoints).count
//        log.warn("U:$inter")
        if (union==0)
            return false


        double ratio = inter / union

        return ratio >= threshold
    }

    @Override
    double score(Ligand ligand, Pocket pocket) {
        return Double.NaN
    }

    @Override
    String toString() {
        "DSOR($threshold)"
    }

}