package cz.siret.prank.features.implementation.contactres

import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.ResidueFeatureCalculationContext
import cz.siret.prank.features.api.ResidueFeatureCalculator
import cz.siret.prank.geom.Atoms
import groovy.transform.CompileStatic

/**
 * Contact Residues Residue Feature
 */
@CompileStatic
class ContactResiduesRF extends ResidueFeatureCalculator {

    static final double CONTACT_ATOM_DIST = 3.3d

    final List<String> HEADER = ['n','n_atoms','n_head','n_side']


    @Override
    String getName() {
        return 'contactres'
    }

    @Override
    List<String> getHeader() {
        return HEADER
    }

    @Override
    double[] calculateForResidue(Residue residue, ResidueFeatureCalculationContext context) {

        Atoms contactAtoms = context.protein.proteinAtoms.cutoutShell(residue.atoms, CONTACT_ATOM_DIST).without(residue.atoms)
        Atoms headCa = contactAtoms.cutoutShell(residue.headAtoms, CONTACT_ATOM_DIST)
        Atoms sideCa = contactAtoms.cutoutShell(residue.sideChainAtoms, CONTACT_ATOM_DIST)

        double n = context.protein.residues.getDistinctForAtoms(contactAtoms).size()
        double n_atoms = contactAtoms.count
        double n_head = context.protein.residues.getDistinctForAtoms(headCa).size()
        double n_side = context.protein.residues.getDistinctForAtoms(sideCa).size()

        return [n, n_atoms, n_head, n_side] as double[]
    }

}
