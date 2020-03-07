package cz.siret.prank.domain.labeling


import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.Residues
import cz.siret.prank.program.params.Parametrized

/**
 *
 *
 */
class LigandBasedResidueLabeler extends ResidueLabeler<Boolean> implements Parametrized {

    double DIST_THRESHOLD = params.ligand_protein_contact_distance

    ResidueLabeling<Double> ligandDistanceLabelng


    @Override
    ResidueLabeling<Boolean> labelResidues(Residues residues, Protein protein) {

        ligandDistanceLabelng = new ResidueLabeling<>(residues.count)
        for (Residue res : residues) {
            double dist = protein.allLigandAtoms.dist(res.atoms)
            ligandDistanceLabelng.add(res, dist)
        }

        BinaryLabeling resLabels = new BinaryLabeling(residues.count)
        for (LabeledResidue<Double> it : ligandDistanceLabelng.labeledResidues) {
            boolean positive = it.label <= DIST_THRESHOLD
            resLabels.add(it.residue, positive)
        }

        return resLabels
    }

    @Override
    boolean isBinary() {
        return true
    }

    @Override
    ResidueLabeling<Double> getDoubleLabeling() {
        return ligandDistanceLabelng
    }

}
