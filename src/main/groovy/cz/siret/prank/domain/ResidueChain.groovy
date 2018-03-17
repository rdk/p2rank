package cz.siret.prank.domain

/**
 *
 */
class ResidueChain {

    /** PDB id */
    String id
    List<Residue> residues

    ResidueChain(String id, List<Residue> residues) {
        this.id = id
        this.residues = residues
    }

    int getSize() {
        residues.size()
    }

    String getCodeCharString() {
        residues.collect { r -> r.codeChar }.join("")
    }
    
}
