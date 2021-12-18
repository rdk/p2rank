package cz.siret.prank.domain

import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic

import javax.annotation.Nonnull

/**
 *
 */
@CompileStatic
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

    List<Residue.SsSection> secStructSections

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

    /**
     * One letter AA code string, with "?" for unknown
     */
    @Nonnull
    String getBiojavaCodeCharString() {
        residues.collect { it.codeCharBiojava }.join("")
    }

    /**
     * One letter AA code string, with "?" for unknown
     */
    @Nonnull
    String getStandardCodeCharString() {
        residues.collect { it.codeCharStandard }.join("")
    }

    @Nonnull
    String getSecStructString() {
        residues.collect { it.secStruct?.type ?: "?" }.join("")
    }

    @Nonnull
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
