package cz.siret.prank.domain.labeling

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.program.PrankException
import groovy.transform.CompileStatic

/**
 * Provides ResidueLabeling for proteins.
 */
@CompileStatic
abstract class ResidueLabeler<L> {

    abstract ResidueLabeling<L> labelResidues(List<Residue> residues, Protein protein)

    abstract boolean isBinary()

    BinaryResidueLabeling getBinaryLabeling(List<Residue> residues, Protein protein) {
        if (isBinary()) {
            (BinaryResidueLabeling) labelResidues(residues, protein)
        } else {
            throw new PrankException("Residue labeler not binary!")
        }
    }

    static ResidueLabeler loadFromFile(String format, String fname) {
        switch (format) {
            case "sprint":
                return SprintLabelingLoader.loadFromFile(fname)
            default:
                throw new PrankException("Invalid labeling file format: " + format)
        }
    }

}
