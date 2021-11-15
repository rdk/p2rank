package cz.siret.prank.features.api

import cz.siret.prank.domain.Protein
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Context for calculation of atom feature.
 */
@CompileStatic
class AtomFeatureCalculationContext {

    Protein protein

    /**
     * 3-letter code of amino acid residue of the atom (all uppercase)
     */
    String residueCode

    AtomFeatureCalculationContext(Protein protein, String residueCode) {
        this.protein = protein
        this.residueCode = residueCode
    }

    AtomFeatureCalculationContext(Protein protein, Atom atom) {
        this(protein, PdbUtils.getCorrectedAtomResidueCode(atom))
    }
    
}
