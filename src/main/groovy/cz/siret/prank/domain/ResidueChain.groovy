package cz.siret.prank.domain

import cz.siret.prank.geom.Atoms
import org.codehaus.groovy.runtime.StringGroovyMethods

/**
 *
 */
class ResidueChain {


    /**
     * ID in an old PDB model.
     * Same as chain letter in ATOM/HETATM record in pdb file.
     * May not be unique among protein chains, but should be unique among protein (polymer) chains.
     */
    String authorId

    /**
     * ID in new mmcif model,
     * May be different from chain letter in ATOM/HETATM record in pdb file.
     */
    String mmcifId

    List<Residue> residues

    Atoms atoms

    ResidueChain(String authorId, String mmcifId,  List<Residue> residues) {
        this.authorId = authorId
        this.mmcifId = mmcifId
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

    /**
     * @return authorId if authorId and mmcifId are the same, composite label otherwise
     */
    String getLabel() {
        (authorId == mmcifId) ? authorId : "$authorId(id:$mmcifId)"
    }

    String getLabelWithLength() {
        getLabel() + "(${residues.size()})"
    }
    
}
