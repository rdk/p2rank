package cz.siret.prank.domain.labeling


import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.Residues
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic

import javax.annotation.concurrent.NotThreadSafe

/**
 *
 * keeps internal state, should be used per-protein
 */
@NotThreadSafe
@CompileStatic
class LigandBasedResidueLabeler extends ResidueLabeler<Boolean> implements Parametrized {

    double DIST_THRESHOLD = params.ligand_protein_contact_distance

    ResidueLabeling<Double> lastLigandDistanceLabeling

    @Override
    ResidueLabeling<Boolean> labelResidues(Residues residues, Protein protein) {

        ResidueLabeling<Double> ligandDistanceLabelng = new ResidueLabeling<>(residues.count)
        for (Residue res : residues) {
            double dist = protein.allRelevantLigandAtoms.dist(res.atoms)
            ligandDistanceLabelng.add(res, dist)
        }

        BinaryLabeling resLabels = new BinaryLabeling(residues.count)
        for (LabeledResidue<Double> it : ligandDistanceLabelng.labeledResidues) {
            boolean positive = it.label <= DIST_THRESHOLD
            resLabels.add(it.residue, positive)
        }

        this.lastLigandDistanceLabeling = ligandDistanceLabelng

        return resLabels
    }

    @Override
    boolean isBinary() {
        return true
    }

    @Override
    ResidueLabeling<Double> getDoubleLabeling() {
        return lastLigandDistanceLabeling
    }

}
