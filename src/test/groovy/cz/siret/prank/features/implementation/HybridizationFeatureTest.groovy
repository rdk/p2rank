package cz.siret.prank.features.implementation

import cz.siret.prank.features.implementation.table.PropertyTable
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class HybridizationFeatureTest {

    @Test
    void testCsvLoads() {
        PropertyTable table = PropertyTable.parseResource("/tables/atom-hybridization.csv")
        assertNotNull(table)
        assertTrue(table.itemNames.size() >= 167, "Expected at least 167 atom entries")
        assertTrue(table.propertyNames.contains('hyb_sp2'))
        assertTrue(table.propertyNames.contains('hyb_sp3'))
    }

    @Test
    void testCsvValues() {
        PropertyTable table = HybridizationFeature.hybTable

        // backbone sp2
        assertEquals(1d, table.getValue('ALA.N', 'hyb_sp2'))
        assertEquals(0d, table.getValue('ALA.N', 'hyb_sp3'))
        assertEquals(1d, table.getValue('ALA.C', 'hyb_sp2'))
        assertEquals(1d, table.getValue('ALA.O', 'hyb_sp2'))

        // backbone sp3
        assertEquals(0d, table.getValue('ALA.CA', 'hyb_sp2'))
        assertEquals(1d, table.getValue('ALA.CA', 'hyb_sp3'))

        // sidechain sp3
        assertEquals(0d, table.getValue('ALA.CB', 'hyb_sp2'))
        assertEquals(1d, table.getValue('ALA.CB', 'hyb_sp3'))
        assertEquals(1d, table.getValue('LYS.NZ', 'hyb_sp3'))
        assertEquals(1d, table.getValue('CYS.SG', 'hyb_sp3'))
        assertEquals(1d, table.getValue('TYR.OH', 'hyb_sp3'))

        // aromatic ring sp2
        assertEquals(1d, table.getValue('PHE.CG', 'hyb_sp2'))
        assertEquals(1d, table.getValue('PHE.CD1', 'hyb_sp2'))
        assertEquals(1d, table.getValue('TRP.NE1', 'hyb_sp2'))
        assertEquals(1d, table.getValue('HIS.CE1', 'hyb_sp2'))
        assertEquals(1d, table.getValue('HIS.NE2', 'hyb_sp2'))

        // functional group sp2
        assertEquals(1d, table.getValue('ARG.CZ', 'hyb_sp2'))
        assertEquals(1d, table.getValue('ARG.NH1', 'hyb_sp2'))
        assertEquals(1d, table.getValue('ASP.CG', 'hyb_sp2'))
        assertEquals(1d, table.getValue('ASP.OD1', 'hyb_sp2'))
        assertEquals(1d, table.getValue('GLN.CD', 'hyb_sp2'))
        assertEquals(1d, table.getValue('GLN.NE2', 'hyb_sp2'))
    }

    @Test
    void testOneHotConsistency() {
        PropertyTable table = HybridizationFeature.hybTable

        for (String item : table.itemNames) {
            double sp2 = table.getValue(item, 'hyb_sp2')
            double sp3 = table.getValue(item, 'hyb_sp3')
            assertEquals(1d, sp2 + sp3, "One-hot should sum to 1 for $item")
            assertTrue(sp2 == 0d || sp2 == 1d, "hyb_sp2 should be 0 or 1 for $item")
            assertTrue(sp3 == 0d || sp3 == 1d, "hyb_sp3 should be 0 or 1 for $item")
        }
    }

}
