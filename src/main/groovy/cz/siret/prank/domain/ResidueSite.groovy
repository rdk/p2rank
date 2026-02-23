package cz.siret.prank.domain

import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 * Binding site defined as a set of residues.
 * Used as ground truth for site-based evaluation.
 */
@Slf4j
@CompileStatic
class ResidueSite implements BindingSite, Parametrized {

    String name
    List<Residue> residues
    Protein protein

    private Atoms cachedAtoms
    private Atoms sasPoints

    ResidueSite(String name, List<Residue> residues, Protein protein) {
        assert !residues.isEmpty(), "ResidueSite must have at least one residue"

        this.name = name
        this.residues = residues
        this.protein = protein
    }

    @Override
    Atoms getAtoms() {
        if (cachedAtoms == null) {
            cachedAtoms = Atoms.union((List<Atoms>) residues.collect { Residue r -> r.atoms })
        }
        return cachedAtoms
    }

    @Override
    Atom getCentroid() {
        return getAtoms().centerOfMass
    }

    @Override
    Atoms getSasPoints() {
        if (sasPoints == null) {
            sasPoints = protein.accessibleSurface.points.cutoutShell(getAtoms(), params.ligand_induced_volume_cutoff)
        }
        return sasPoints
    }

    @Override
    String getLabel() {
        return name
    }

    @Override
    String toString() {
        return "site $name residues:${residues.size()}"
    }

}
