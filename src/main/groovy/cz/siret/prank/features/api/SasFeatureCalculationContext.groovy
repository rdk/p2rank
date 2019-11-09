package cz.siret.prank.features.api

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.PrankFeatureExtractor
import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic

/**
 * Context for calculation of a SAS feature.
 */
@CompileStatic
class SasFeatureCalculationContext {

    Protein protein
    Atoms neighbourhoodAtoms

    /**
     * this is kind of a backdoor, should be avoided when implementing new features
     */
    @Deprecated
    PrankFeatureExtractor extractor

    SasFeatureCalculationContext(Protein protein, Atoms neighbourhoodAtoms, PrankFeatureExtractor extractor) {
        this.protein = protein
        this.neighbourhoodAtoms = neighbourhoodAtoms
        this.extractor = extractor
    }
    
}
