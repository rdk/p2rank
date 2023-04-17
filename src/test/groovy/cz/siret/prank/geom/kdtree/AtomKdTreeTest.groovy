package cz.siret.prank.geom.kdtree

import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 *
 */
@CompileStatic
class AtomKdTreeTest implements Writable {

    @Test
    void findAtomsWithinRadius() {
        test_findAtomsWithinRadius('distro/test_data/2W83.pdb')
        test_findAtomsWithinRadius('distro/test_data/1fbl.pdb.gz')
    }

    void test_findAtomsWithinRadius(String fname) {
        Protein p = Protein.load(fname)

        double RADIUS = 6d

        Atoms atoms = p.proteinAtoms.withKdTree()

        for (Atom a : atoms) {
            Atoms serial = atoms.cutoutSphere(a, RADIUS)
            Atoms kd = atoms.cutoutSphereKD(a, RADIUS)

            // write "findAtomsWithinRadius serial:$serial.count kd:$kd.count"

            //assertEquals(serial.count, kd.count)
            assertEquals(serial.toSet(), kd.toSet())
        }

    }

}