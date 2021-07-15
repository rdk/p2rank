package cz.siret.prank.domain.labeling

import cz.siret.prank.domain.Residue
import groovy.transform.CompileStatic

/**
 * Residue with label
 */
@CompileStatic
class LabeledResidue<L> {

    private Residue residue
    private L label

    LabeledResidue(Residue residue, L label) {
        this.residue = residue
        this.label = label
    }

    Residue getResidue() {
        return residue
    }

    L getLabel() {
        return label
    }

}
