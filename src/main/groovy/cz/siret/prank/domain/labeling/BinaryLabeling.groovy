package cz.siret.prank.domain.labeling

import groovy.transform.CompileStatic

/**
 * Binary residue labeling
 */
@CompileStatic
class BinaryLabeling extends ResidueLabeling<Boolean> {

    BinaryLabeling(int initSize) {
        super(initSize)
    }

    BinaryLabeling(List<LabeledResidue<Boolean>> labeledResidues) {
        super(labeledResidues)
    }
    
}
