package cz.siret.prank.geom

import cz.siret.prank.domain.Protein;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
class StructTest {

    @Test
    void getResiduesFromChain() {
        Protein p = Protein.load('src/test/resources/data/2nbr.pdb.gz')
        assertTrue 173 == Struct.getResiduesFromChain(p.structure.getChainByPDB("A")).size()

        // chain with amino acid ligand (that should be excluded)
        Protein p2 = Protein.load('src/test/resources/data/11as.pdb.gz')
        assertTrue 327 == Struct.getResiduesFromChain(p2.structure.getChainByPDB("A")).size()



        // TODo add test for chain with phosphorylated residue
    }


}