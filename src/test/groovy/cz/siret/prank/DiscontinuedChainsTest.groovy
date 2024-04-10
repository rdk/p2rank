package cz.siret.prank

import cz.siret.prank.domain.Protein
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 *
 */
@Slf4j
@CompileStatic
class DiscontinuedChainsTest {

    String PDB_1O6U = 'src/test/resources/data/tricky_cases/1o6u.pdb'
    String CIF_1O6U = 'src/test/resources/data/tricky_cases/1o6u.cif'

    @Test
    void discontinuedChainsLoading_1O6U_pdb() throws Exception {
        Protein prot = Protein.load(PDB_1O6U)

        def chainA = prot.getResidueChain('A')
        def chainC = prot.getResidueChain('C')


        log.info "All Atoms: {}", prot.allAtoms.count
        log.info "Protein Atoms: {}", prot.proteinAtoms.count
        log.info "Chain A Length: {}", chainA.length
        log.info "Chain C Length: {}", chainC.length

        assertEquals(394, chainA.length, "Expected length of chain A")
        assertEquals(387, chainC.length, "Expected length of chain C")
        assertEquals(10203, prot.allAtoms.count, "Expected number of all structure atoms")
        assertEquals(9311, prot.proteinAtoms.count, "Expected number of protein atoms")


        // P2Rank 2.4.1
        // All Atoms: 10203
        // Protein Atoms: 8229
        // Chain A Length: 321
        // Chain C Length: 322

        // P2Rank after fix
        // All Atoms: 10203
        // Protein Atoms: 9311
        // Chain A Length: 394
        // Chain C Length: 387
        
    }

    @Test
    void discontinuedChainsLoading_1O6U_cif() throws Exception {
        Protein prot = Protein.load(CIF_1O6U)

        def chainA = prot.getResidueChain('A')
        def chainC = prot.getResidueChain('C')

        assertEquals(394, chainA.length, "Expected length of chain A")
        assertEquals(387, chainC.length, "Expected length of chain C")
        assertEquals(10203, prot.allAtoms.count, "Expected number of all structure atoms")
        assertEquals(9311, prot.proteinAtoms.count, "Expected number of protein atoms")

    }
    
}
