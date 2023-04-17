package cz.siret.prank.domain.labeling

import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 *
 */
@Slf4j
@CompileStatic
class LigandBasedPointLabeler extends PointLabeler implements Parametrized {

    /**
     * labeled points with __observed__ value set
     */
    @Override
    List<LabeledPoint> labelPoints(Atoms points, Protein protein) {
        List<LabeledPoint> res = new ArrayList<>(points.size())

        Atoms ligandAtoms = protein.allRelevantLigandAtoms
        for (Atom point : points) {
            boolean observed = ligandAtoms.areWithinDistance(point, params.positive_point_ligand_distance)
            res.add(new LabeledPoint(point, observed))
        }

        return res
    }

}
