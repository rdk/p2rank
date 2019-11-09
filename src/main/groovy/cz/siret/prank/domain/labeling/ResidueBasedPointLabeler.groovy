package cz.siret.prank.domain.labeling

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 * Labels points according to nearest residue.
 */
@Slf4j
@CompileStatic
class ResidueBasedPointLabeler extends PointLabeler {

    BinaryLabeling residueLabeling

    ResidueBasedPointLabeler(BinaryLabeling residueLabeling) {
        this.residueLabeling = residueLabeling
    }
    
    /**
     * labeled points with __observed__ value set
     */
    @Override
    List<LabeledPoint> labelPoints(Atoms points, Protein protein) {
        List<LabeledPoint> res = new ArrayList<>(points.size())

        for (Atom point : points) {
            // label point by closest residue
            Residue nearestRes = protein.residues.findNearest(point)
            Boolean label = residueLabeling.getLabel(nearestRes)

            boolean observed
            if (label != null) {
                observed = label
            } else {
                log.warn("Label for residue [{}] not found. This shouldn't happen!", nearestRes.key)
                observed = false
            }

            res.add(new LabeledPoint(point, observed))
        }

        return res
    }

}
