package cz.siret.prank.domain

import cz.siret.prank.domain.loaders.LoaderParams
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 *
 */
@Slf4j
@CompileStatic
class DatasetTest {


    @Test
    void testLigandDefinitionsAndSpecificLigandsLoading() {
        LoaderParams.ignoreLigandsSwitch = false

        Dataset ds = Dataset.loadFromFile('distro/test_data/specified-ligands-2.ds')

        assertEquals 3, ds.items[0].predictionPair.ligandCount
        assertEquals 3, ds.items[1].predictionPair.ligandCount
        assertEquals 6, ds.items[2].predictionPair.ligandCount
        assertEquals 1, ds.items[3].predictionPair.ligandCount
        assertEquals 1, ds.items[4].predictionPair.ligandCount
        assertEquals 1, ds.items[5].predictionPair.ligandCount
        assertEquals 1, ds.items[6].predictionPair.ligandCount
        assertEquals 1, ds.items[7].predictionPair.ligandCount
        assertEquals 1, ds.items[8].predictionPair.ligandCount
        assertEquals 2, ds.items[9].predictionPair.ligandCount
        assertEquals 4, ds.items[10].predictionPair.ligandCount

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
        LoaderParams.ignoreLigandsSwitch = false

        Dataset ds = Dataset.loadFromFile('distro/test_data/specified-chains-and-ligands.ds')

        assertEquals 3, ds.items[0].predictionPair.ligandCount
        assertEquals 3, ds.items[1].predictionPair.ligandCount
        assertEquals 3, ds.items[2].predictionPair.ligandCount
        assertEquals 3, ds.items[3].predictionPair.ligandCount
        assertEquals 6, ds.items[4].predictionPair.ligandCount
        assertEquals 6, ds.items[5].predictionPair.ligandCount
        assertEquals 1, ds.items[6].predictionPair.ligandCount
        assertEquals 1, ds.items[7].predictionPair.ligandCount
        assertEquals 1, ds.items[8].predictionPair.ligandCount
        assertEquals 2, ds.items[9].predictionPair.ligandCount
        assertEquals 3, ds.items[10].predictionPair.ligandCount

    }

    /**
     * Note: mmCIF files in this dataset were produced by PyMol and don't contain auth_seq_id column in ATOM records
     */
    @Test
    void testApoHoloDatasetLoading() {
        LoaderParams.ignoreLigandsSwitch = false

        Dataset ds = Dataset.loadFromFile('distro/test_data/apoholo/zn_apoholo.ds')

        assertEquals 1, ds.items[0].predictionPair.ligandCount
        assertEquals 1, ds.items[1].predictionPair.ligandCount
        assertEquals 1, ds.items[2].predictionPair.ligandCount
        assertEquals 1, ds.items[3].predictionPair.ligandCount
        assertEquals 1, ds.items[4].predictionPair.ligandCount

        assertEquals 1, ds.items[0].chains.size()
        assertEquals 1, ds.items[1].chains.size()
        assertEquals 1, ds.items[2].chains.size()
        assertEquals 1, ds.items[3].chains.size()
        assertEquals 1, ds.items[4].chains.size()

        assertEquals 1, ds.items[0].apoChains.size()
        assertEquals 1, ds.items[1].apoChains.size()
        assertEquals 1, ds.items[2].apoChains.size()
        assertEquals 1, ds.items[3].apoChains.size()
        assertEquals 1, ds.items[4].apoChains.size()

        assertNotEquals null, ds.items[0].apoProtein
        assertNotEquals null, ds.items[1].apoProtein
        assertNotEquals null, ds.items[2].apoProtein
        assertNotEquals null, ds.items[3].apoProtein
        assertNotEquals null, ds.items[4].apoProtein
    }

    @Test
    void secondaryStructureAssignment() {
        Dataset ds1 = Dataset.loadFromFile('distro/test_data/specified-chains.ds')
        Dataset ds2 = Dataset.loadFromFile('distro/test_data/specified-chains-and-ligands.ds')

        [ds1, ds2].each {
            it.processItems { Dataset.Item item ->
                item.protein.assignSecondaryStructure()
                item.protein.residueChains.each {assertNotNull it.secStructSections }
            }
        }
    }

}