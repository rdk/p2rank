package cz.siret.prank.geom

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.utils.Writable
import org.biojava.nbio.structure.Atom
import org.junit.Test;

import static org.junit.Assert.*

/**
 * 
 */
class AtomsTest implements Writable {

    @Test
    void cutoutShell() {
        test_cutoutShell('distro/test_data/2W83.pdb')
        test_cutoutShell('distro/test_data/1fbl.pdb.gz')
    }

    void test_cutoutShell(String fname) {
        Protein p = Protein.load(fname)

        double DIST = 4d

        Atoms atoms = p.proteinAtoms.withKdTree()

        for (Residue res : p.residues) {
            Atoms serial = atoms.cutoutShell(res.atoms, DIST)
            Atoms kd = Atoms.cutoutShell(atoms, res.atoms, DIST)

            write "findAtomsWithinRadius serial:$serial.count kd:$kd.count"

            //assertEquals(serial.count, kd.count)
            assertEquals(serial.toSet(), kd.toSet())
        }

    }

}
