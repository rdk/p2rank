package cz.siret.prank.features.implementation.table


import org.junit.jupiter.api.Test

/**
 *
 */
class PropertyTableTest {

    void doTestParseTableFromResource(String resourcePath) {
        PropertyTable table = PropertyTable.parseResource(resourcePath)
    }


    @Test
    void parseCsvPropertyTables() throws Exception {
        doTestParseTableFromResource("/tables/atomic-properties.csv")
        doTestParseTableFromResource("/tables/aa-propensities.csv")
        doTestParseTableFromResource("/tables/aa-5factors.csv")
        doTestParseTableFromResource("/tables/aa-index-full.csv")
    }

}
