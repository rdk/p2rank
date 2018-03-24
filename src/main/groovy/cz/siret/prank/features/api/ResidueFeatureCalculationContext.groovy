package cz.siret.prank.features.api

import cz.siret.prank.domain.Protein
import groovy.transform.CompileStatic

/**
 * Context for calculation of residue feature.
 */
@CompileStatic
class ResidueFeatureCalculationContext {

    Protein protein

    ResidueFeatureCalculationContext(Protein protein) {
        this.protein = protein
    }
    
}
