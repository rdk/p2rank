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

    static final double CONTACT_ATOM_DIST = 3.5d

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
        Atoms backboneContactAtoms = contactAtoms.cutoutShell(residue.backboneAtoms, CONTACT_ATOM_DIST)
        Atoms sidechainContactAtoms = contactAtoms.cutoutShell(residue.sidechainAtoms, CONTACT_ATOM_DIST)

        double n = context.protein.residues.getDistinctForAtoms(contactAtoms).size()
        double n_atoms = contactAtoms.count
        double n_head = context.protein.residues.getDistinctForAtoms(backboneContactAtoms).size()
        double n_side = context.protein.residues.getDistinctForAtoms(sidechainContactAtoms).size()

        return [n, n_atoms, n_head, n_side] as double[]
    }

}
