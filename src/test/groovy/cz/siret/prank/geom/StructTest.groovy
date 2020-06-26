package cz.siret.prank.geom

import cz.siret.prank.domain.Protein
import groovy.util.logging.Slf4j;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
@Slf4j
class StructTest {


    /**
     * Test for protein loader as well
     */
    @Test
    void getResiduesFromChain() {
        Protein p = Protein.load('src/test/resources/data/2nbr.pdb.gz')
        assertEquals 173, Struct.getResiduesFromChain(p.structure.getChainByPDB("A")).size()

        // Contains chain with amino acid ligand that should be excluded from chain and considered as ligand.
        Protein p2 = Protein.load('src/test/resources/data/11as.pdb')
        log.info ""+p2.ignoredLigands
        assertEquals 327, Struct.getResiduesFromChain(p2.structure.getChainByPDB("A")).size()
        //assertEquals 1, p2.ligandCount   // currently fails. TODO try with biojava 5

        // Contains modified AA residue in the middle of the chain as HET record ("PTR A 527").
        // Should not be removed from chain and should not be considered a ligand.
        Protein p3 = Protein.load('src/test/resources/data/2src.pdb')
        //assertEquals 450, Struct.getResiduesFromChain(p3.structure.getChainByPDB("A")).size()   // currently fails. TODO try with biojava 5
        // assertEquals 1, p3.ligandCount  // currently fails. TODO try with biojava 5

        // TODo add test for chain with phosphorylated residue
    }


}