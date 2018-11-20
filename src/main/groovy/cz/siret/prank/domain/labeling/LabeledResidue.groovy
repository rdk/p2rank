package cz.siret.prank.domain.labeling

import cz.siret.prank.domain.Residue

/**
 * Residue with label
 */
class LabeledResidue<L> {

    private Residue residue
//   private L label  // TODo make private agsain
    L label

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
