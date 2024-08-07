package cz.siret.prank.domain.labeling

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.Residues
import cz.siret.prank.utils.Sutils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 *
 */
@Slf4j
@CompileStatic
class ColumnBasedResidueLabeler extends ResidueLabeler<Boolean> implements Writable {

    @Override
    ResidueLabeling<Boolean> labelResidues(Residues residues, Protein protein, Dataset.Item item) {

        Set<String> positives = Sutils.split(item.columnValues.get(Dataset.COLUMN_POSITIVE_RESIDUES), ',').toSet()

        return createLabelingFromPositiveResidueCodes(residues, positives)
    }

    @Override
    boolean isBinary() {
        return true
    }

    @Override
    ResidueLabeling<Double> getDoubleLabeling() {
        return null
    }

//===========================================================================================================//

    static ResidueLabeling<Boolean> createLabelingFromPositiveResidueCodes(Residues residues, Set<String> positives) {
        ResidueLabeling<Boolean> labeling = new ResidueLabeling<Boolean>()

        for (Residue residue : residues) {
            boolean label = positives.contains(residue.toString())
            labeling.add(residue, label)
        }

        return labeling
    }

}
