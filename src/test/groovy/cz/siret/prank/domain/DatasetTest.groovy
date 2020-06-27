package cz.siret.prank.domain

import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
@Slf4j
@CompileStatic
class DatasetTest {


    @Test
    void testLigandDefinitionsAndSpecificLigandsLoading() {
        Dataset ds = Dataset.loadFromFile('distro/test_data/specified-ligands-2.ds')

        assertEquals 3, ds.items[0].protein.ligandCount
        assertEquals 3, ds.items[1].protein.ligandCount
        assertEquals 6, ds.items[2].protein.ligandCount
        assertEquals 1, ds.items[3].protein.ligandCount
        assertEquals 1, ds.items[4].protein.ligandCount
        assertEquals 1, ds.items[5].protein.ligandCount
        assertEquals 1, ds.items[6].protein.ligandCount
        assertEquals 1, ds.items[7].protein.ligandCount
        assertEquals 1, ds.items[8].protein.ligandCount
        assertEquals 2, ds.items[9].protein.ligandCount
        assertEquals 4, ds.items[10].protein.ligandCount

    }

    @Test
    void testReducedStructures() {
        Dataset ds = Dataset.loadFromFile('distro/test_data/specified-chains.ds')

        assertEquals 1, ds.items[0].protein.residueChains.size()
        assertEquals 1, ds.items[1].protein.residueChains.size()
        assertEquals 1, ds.items[2].protein.residueChains.size()
        assertEquals 1, ds.items[3].protein.residueChains.size()
        assertEquals 1, ds.items[4].protein.residueChains.size()
        assertEquals 2, ds.items[5].protein.residueChains.size()
        assertEquals 3, ds.items[6].protein.residueChains.size()
        assertEquals 4, ds.items[7].protein.residueChains.size()
        assertEquals 5, ds.items[8].protein.residueChains.size()
        assertEquals 5, ds.items[9].protein.residueChains.size()

    }

    @Test
    void testReducedStructresWithSpecifiedLigands() {
        Dataset ds = Dataset.loadFromFile('distro/test_data/specified-chains-and-ligands.ds')

        assertEquals 3, ds.items[0].protein.ligandCount
        assertEquals 3, ds.items[1].protein.ligandCount
        assertEquals 3, ds.items[2].protein.ligandCount
        assertEquals 3, ds.items[3].protein.ligandCount
        assertEquals 6, ds.items[4].protein.ligandCount
        assertEquals 6, ds.items[5].protein.ligandCount
        assertEquals 1, ds.items[6].protein.ligandCount
        assertEquals 1, ds.items[7].protein.ligandCount
        assertEquals 1, ds.items[8].protein.ligandCount
        assertEquals 2, ds.items[9].protein.ligandCount
        assertEquals 3, ds.items[10].protein.ligandCount

    }

}