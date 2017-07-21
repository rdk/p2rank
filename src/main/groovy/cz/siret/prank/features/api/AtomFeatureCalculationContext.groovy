package cz.siret.prank.features.api

import cz.siret.prank.domain.Protein
import groovy.transform.CompileStatic

/**
 * Context for calculation of atom feature.
 */
@CompileStatic
class AtomFeatureCalculationContext {

    Protein protein

    /**
     * 3-letter code of amino acid residue of the atom
     */
    String residueCode

    AtomFeatureCalculationContext(Protein protein, String residueCode) {
        this.protein = protein
        this.residueCode = residueCode
    }
}
