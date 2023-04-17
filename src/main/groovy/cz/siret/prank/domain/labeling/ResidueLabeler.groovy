package cz.siret.prank.domain.labeling


import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residues
import cz.siret.prank.program.PrankException
import groovy.transform.CompileStatic

import javax.annotation.Nullable

/**
 * Provides ResidueLabeling for proteins.
 */
@CompileStatic
abstract class ResidueLabeler<L> {

    abstract ResidueLabeling<L> labelResidues(Residues residues, Protein protein)

    abstract boolean isBinary()

    @Nullable
    abstract ResidueLabeling<Double> getDoubleLabeling()

    BinaryLabeling getBinaryLabeling(Residues residues, Protein protein) {
        if (isBinary()) {
            (BinaryLabeling) labelResidues(residues, protein)
        } else {
            throw new PrankException("Residue labeler not binary!")
        }
    }

    BinaryLabeling getBinaryLabeling(Protein protein) {
        getBinaryLabeling(protein.residues, protein)
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
