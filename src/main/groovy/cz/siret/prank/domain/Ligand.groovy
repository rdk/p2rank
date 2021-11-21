package cz.siret.prank.domain

import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group

/**
 * Ligand made of one or several pdb groups
 */
@Slf4j
@CompileStatic
class Ligand implements Parametrized {

    /**
     * pdb group name
     */
    String name

    /**
     * group id(s) (resCode in pdb)
     */
    String code

    /**
     * chain code(s)
     */
    String chain

    Atoms atoms
    Protein protein
    double contactDistance
    double centerToProteinDist

    /** distinct atom groups */
    List<Group> groups

    /**
     * SAS points induced by the ligand
     */
    Atoms sasPoints

    Pocket predictedPocket


    Ligand(Atoms ligAtoms, Protein protein) {
        assert !ligAtoms.empty , "Trying to create ligand with no atoms!"

        atoms = new Atoms(ligAtoms)
        this.protein = protein
        this.groups = atoms.getDistinctGroupsSorted()
        Set<String> uniqueNames = (groups*.PDBName).toSet()
        this.name = uniqueNames.join("&")
        this.code = (groups*.residueNumber).join("&")
        this.chain = (groups*.chainId).toSet().toSorted().join("&")

        for (Atom a : atoms) {
            PdbUtils.correctBioJavaElement(a)
        }

        if (log.debugEnabled) {
            groups.each { Group g ->
                log.debug "\tligand group: $g.PDBName [$g.residueNumber] atoms:" + Atoms.allFromGroup(g).count + " component: " + g.getChemComp()
            }
        }

        log.info this.toString()
    }

    Atoms calcContactAtoms(Atoms proteinAtoms) {
        return proteinAtoms.cutoutShell(atoms, params.ligand_protein_contact_distance)
    }

    Atoms getSasPoints() {
        if (sasPoints==null) {
            sasPoints = protein.accessibleSurface.points.cutoutShell(this.atoms, params.ligand_induced_volume_cutoff)
        }
        return sasPoints
    }

    Atom getCentroid() {
        atoms.centerOfMass
        //atoms.geometricCenter
    }

    String getNameCode() {
        name + "_" + code
    }

    int getSize() {
        atoms.count
    }

    @Override
    public String toString() {
        return "ligand $name atoms:$atoms.count"
    }

}
