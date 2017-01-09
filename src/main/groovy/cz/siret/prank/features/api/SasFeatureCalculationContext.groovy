package cz.siret.prank.features.api

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.chemproperties.ChemFeatureExtractor
import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic

/**
 *
 */
@CompileStatic
class SasFeatureCalculationContext {

    Protein protein
    Atoms neighbourhoodAtoms

    /**
     * this is kind of a backdoor, should be avoided when implementing new features
     */
    @Deprecated
    ChemFeatureExtractor extractor

    SasFeatureCalculationContext(Protein protein, Atoms neighbourhoodAtoms, ChemFeatureExtractor extractor) {
        this.protein = protein
        this.neighbourhoodAtoms = neighbourhoodAtoms
        this.extractor = extractor
    }
}
