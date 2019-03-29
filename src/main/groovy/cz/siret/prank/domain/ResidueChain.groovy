package cz.siret.prank.domain

import cz.siret.prank.geom.Atoms

/**
 *
 */
class ResidueChain {

    /** PDB id */
    String id
    List<Residue> residues

    Atoms atoms

    ResidueChain(String id, List<Residue> residues) {
        this.id = id
        this.residues = residues

        int pos = 0
        for (Residue res : residues) {
            res.chain = this
            res.posInChain = pos++
        }
    }

    int getLength() {
        residues.size()
    }

    String getCodeCharString() {
        residues.collect { it.codeChar }.join("")
    }

    String getSecStructString() {
        residues.collect { it.secStruct?.type ?: "?" }.join("")
    }

    Atoms getAtoms() {
        if (atoms==null) {
            atoms = Atoms.join(residues.collect { it.atoms })
        }
        atoms
    }
    
}
