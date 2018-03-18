package cz.siret.prank.collectors

import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.labeling.BinaryResidueLabeling
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.domain.labeling.ResidueLabeler
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 * Collects vectors with classes derived from residue labeling
 */
@Slf4j
@CompileStatic
class ResidueDerivedPointVectorCollector extends PointVectorCollector {

    ResidueLabeler<Boolean> labeler

    ResidueDerivedPointVectorCollector(FeatureExtractor extractorFactory, ResidueLabeler<Boolean> labeler) {
        super(extractorFactory)
        this.labeler = labeler
    }

    @Override
    List<LabeledPoint> labelPoints(Atoms points, PredictionPair pair, ProcessedItemContext context) {
        Protein prot = pair.queryProtein
        BinaryResidueLabeling labeling = labeler.getBinaryLabeling(prot.proteinResidues, prot)
        Atoms protAtoms = prot.residueAtoms

        List<LabeledPoint> res = new ArrayList<>(points.size())
        for (Atom point : points) {
            // label point by closest residue
            Atom nearestAtom = protAtoms.getKdTree().findNearest(point)
            Residue nearestRes = prot.getResidueForAtom(nearestAtom)
            Boolean label = labeling.getLabel(nearestRes)

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
