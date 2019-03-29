package cz.siret.prank.collectors

import cz.siret.prank.domain.PredictionPair
import cz.siret.prank.domain.labeling.LabeledPoint
import cz.siret.prank.domain.labeling.PointLabeler
import cz.siret.prank.domain.labeling.ResidueBasedPointLabeler
import cz.siret.prank.domain.labeling.ResidueLabeler
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

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
        PointLabeler pointLabeler = new ResidueBasedPointLabeler(labeler.getBinaryLabeling(pair.protein.residues, pair.protein))
        return pointLabeler.labelPoints(points, pair.protein)
    }
    
}
