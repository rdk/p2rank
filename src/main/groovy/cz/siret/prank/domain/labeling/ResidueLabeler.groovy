package cz.siret.prank.domain.labeling

import cz.siret.prank.domain.Dataset
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

    abstract ResidueLabeling<L> labelResidues(Residues residues, Protein protein, Dataset.Item item)

    abstract boolean isBinary()

    @Nullable
    abstract ResidueLabeling<Double> getDoubleLabeling()

    BinaryLabeling getBinaryLabeling(Residues residues, Protein protein, @Nullable Dataset.Item item) {
        if (isBinary()) {
            (BinaryLabeling) labelResidues(residues, protein, item)
        } else {
            throw new PrankException("Residue labeler not binary!")
        }
    }

    BinaryLabeling getBinaryLabeling(Residues residues, Protein protein) {
        return getBinaryLabeling(residues, protein, null)
    }

    BinaryLabeling getBinaryLabeling(Protein protein) {
        getBinaryLabeling(protein.residues, protein, null)
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
