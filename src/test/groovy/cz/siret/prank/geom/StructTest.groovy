package cz.siret.prank.geom

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.AminoAcid
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 *
 */
@Slf4j
@CompileStatic
class StructTest {

    static String dataDir = 'src/test/resources/data'

    /**
     * Test for protein loader as well
     */
    @Test
    void getResiduesFromChain() {
        Protein p = Protein.load("$dataDir/2nbr.pdb.gz")
        assertEquals 173, p.getResidueChain("A").length

        // Contains chain with amino acid ligand that should be excluded from chain and considered as ligand.
        Protein p2 = Protein.load("$dataDir/11as.pdb")
        assertEquals 327, p2.getResidueChain("A").length
        //assertEquals 1, p2.ligandCount   // currently fails

        // Contains modified AA residue in the middle of the chain as HET record ("PTR A 527").
        // Should not be removed from chain and should not be considered a ligand.
        Protein p3 = Protein.load("$dataDir/2src.pdb")
        assertEquals 450, p3.getResidueChain("A").length
        // assertEquals 1, p3.ligandCount     // TODO investigate why this fails in travis but not locally

        // TODo add test for chain with phosphorylated residue
    }

    @Test
    void calcCaCentroidReturnsGeometricCenterOfCaAtoms() {
        Protein p = Protein.load("$dataDir/2nbr.pdb.gz")
        List<Residue> residues = p.residues.toList().subList(0, 5)

        Atom result = Struct.calcCaCentroid(residues)
        assertNotNull(result)

        // Manually compute expected centroid from CA atoms
        List<Atom> caAtoms = []
        for (Residue res : residues) {
            AminoAcid aa = res.aminoAcid
            if (aa != null) {
                Atom ca = aa.getCA()
                if (ca != null) caAtoms.add(ca)
            }
        }
        Atom expected = new Atoms(caAtoms).centroid

        assertEquals(expected.x, result.x, 0.001)
        assertEquals(expected.y, result.y, 0.001)
        assertEquals(expected.z, result.z, 0.001)
    }

    @Test
    void calcCaCentroidReturnsNullForEmptyList() {
        assertNull(Struct.calcCaCentroid([]))
    }

}