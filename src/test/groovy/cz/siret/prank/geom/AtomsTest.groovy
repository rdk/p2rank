package cz.siret.prank.geom

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 * 
 */
@CompileStatic
class AtomsTest implements Writable {

    @Test
    void cutoutShell() {
        test_cutoutShell('distro/test_data/2W83.pdb')
        test_cutoutShell('distro/test_data/1fbl.pdb.gz')
        test_cutoutShell('src/test/resources/data/2nbr.pdb.gz')
    }

    void test_cutoutShell(String fname) {
        Protein p = Protein.load(fname)

        test_cutoutShell(p, p.proteinAtoms, 1)
        test_cutoutShell(p, p.proteinAtoms, 3)
        test_cutoutShell(p, p.proteinAtoms, 6)

        test_cutoutShell(p, p.accessibleSurface.points, 1)
        test_cutoutShell(p, p.accessibleSurface.points, 3)
        test_cutoutShell(p, p.accessibleSurface.points, 6)
        test_cutoutShell(p, p.accessibleSurface.points, 9)

    }

    void test_cutoutShell(Protein p, Atoms fromAtoms, double dist) {
        for (Residue res : p.residues) {
            Atoms kd = fromAtoms.cutoutShell(res.atoms, dist)
            Atoms serial = Atoms.cutoutShell(fromAtoms, res.atoms, dist)

            //write "cutoutShell serial:$serial.count kd:$kd.count"

            //assertEquals(serial.count, kd.count)
            assertEquals(serial.toSet(), kd.toSet())
        }
    }

}
