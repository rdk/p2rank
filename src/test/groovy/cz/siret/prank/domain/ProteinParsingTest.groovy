package cz.siret.prank.domain

import cz.siret.prank.utils.PdbUtils
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
    //@Disabled // until solved in BioJava
    @Test
    void testParsePdbeUpdatedCif() {
        Protein protein = Protein.load("$DIR/4gqq.cif")

        PdbUtils.PARSING_PARAMS.parseSites = false  // using custom forked build with new parseSites param

        protein = Protein.load("$DIR/4gqq_updated.cif")   // fails in 2.5, still fails with BioJava 7.1.4 and 7.2.2
        protein = Protein.load("$DIR/4gqq_updated_2025.cif")   // fails in 2.5, still fails with BioJava 7.1.4 and 7.2.2
    }

    @Test
    void testParsePdbeUpdatedCif2() {
        Protein protein = Protein.load("$DIR/1fbl.cif")

        Protein protein2 = Protein.load("$DIR/1fbl_updated.cif")  // OK in in 2.5
    }

    /**
     * https://github.com/rdk/p2rank/issues/77
     *
     * P2Rank 2.5 (using BioJava 7.1.3) fails with org.rcsb.cif.EmptyColumnException: column pdbx_PDB_id_code is undefined
     * Already fixed in BioJava 7.2.2
     */
    @Test
    void testParse8zz7Cif() {
        Protein protein = Protein.load("$DIR/8zz7.cif")
    }

}
