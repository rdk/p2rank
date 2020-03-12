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


        boolean labelPointsByLigands = !params.predict_residues || params.identify_peptides_by_labeling || params.ligand_derived_point_labeling

        if (labelPointsByLigands) {
            // label (i.e. assign class) to SAS points based on proximity to relevant ligands
            return new LigandabilityPointVectorCollector(extractorFactory)
        } else {
            // label (i.e. assign class) to SAS points based on labeling of nearest residue
            return new ResidueDerivedPointVectorCollector(extractorFactory, dataset.binaryResidueLabeler)
        }
    }

}
