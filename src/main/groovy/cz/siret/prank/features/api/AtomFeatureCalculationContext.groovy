package cz.siret.prank.features.api

import cz.siret.prank.domain.Protein
import groovy.transform.CompileStatic

/**
 * Context for calculation of atom feature.
 */
@CompileStatic
class AtomFeatureCalculationContext {

    Protein protein

    AtomFeatureCalculationContext(Protein protein) {
        this.protein = protein
    }

}
