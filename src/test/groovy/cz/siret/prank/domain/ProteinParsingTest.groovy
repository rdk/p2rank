package cz.siret.prank.domain

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 *
 */
@Slf4j
@CompileStatic
class ProteinParsingTest {

    static final String DIR = 'src/test/resources/data/tricky_cases/parsing'

    /**
     * Test parsing PDBe specific "updates cifs"
     */
    @Disabled // until solved in BioJava
    @Test
    void testParsePdbeUpdatedCif() {
        Protein protein = Protein.load("$DIR/4gqq.cif")

        Protein protein2 = Protein.load("$DIR/4gqq_updated.cif")   // fails in 2.5, still fails with BioJava 7.1.4 and 7.2.0
    }

    @Test
    void testParsePdbeUpdatedCif2() {
        Protein protein = Protein.load("$DIR/1fbl.cif")

        Protein protein2 = Protein.load("$DIR/1fbl_updated.cif")  // OK in in 2.5
    }

    /**
     * https://github.com/rdk/p2rank/issues/77
     *
     * 2.5 supposedly fails with org.rcsb.cif.EmptyColumnException: column pdbx_PDB_id_code is undefined
     */
    @Test
    void testParse8zz7Cif() {
        Protein protein = Protein.load("$DIR/8zz7.cif")
    }

}
