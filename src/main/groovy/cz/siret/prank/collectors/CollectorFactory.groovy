package cz.siret.prank.collectors

import cz.siret.prank.domain.Dataset
import cz.siret.prank.features.FeatureExtractor
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 *
 */
@Slf4j
@CompileStatic
class CollectorFactory {

    /**
     * Create vector collector based on current parametrization
     */
    static VectorCollector createCollector(FeatureExtractor extractorFactory, Dataset dataset) {
        Params params = Params.getInst()

        if (!params.predict_residues || params.identify_peptides_by_labeling) {
            // mode: labeled residue
            return new LigandabilityPointVectorCollector(extractorFactory)
        } else {
            // mode: ligandable pockets
            return new ResidueDerivedPointVectorCollector(extractorFactory, dataset.binaryResidueLabeler)
        }
    }

}
