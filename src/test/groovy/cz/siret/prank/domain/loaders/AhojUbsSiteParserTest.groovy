package cz.siret.prank.domain.loaders

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests for {@link AhojUbsSiteParser} using both the reduced (9-column)
 * and full (59-column) ahoj_ubs CSV formats.
 *
 * The two formats differ in column count and order, but share the column names
 * that the parser needs. The full format also contains quoted fields with
 * embedded commas, verifying that the CSV library handles quoting correctly.
 */
@CompileStatic
class AhojUbsSiteParserTest {

    static final String DATA_DIR = 'src/test/resources/data/datasets/ahojubs'
    static final String REDUCED_CSV = "$DATA_DIR/ahojubs_reduced.csv"
    static final String FULL_CSV = "$DATA_DIR/ahojubs_full.csv.gz"

    static final double DELTA = 0.0001d

    // --- Reduced CSV tests ---

    @Test
    void testParseReducedCsv() {
        ExplicitSitesIndex index = AhojUbsSiteParser.parse(REDUCED_CSV)

        // Count all sites across all proteins
        int totalSites = 0
        int proteinCount = 0
        index.@byFilename.each { String filename, List<ExplicitSitesIndex.SiteDef> defs ->
            totalSites += defs.size()
            proteinCount++
        }

        assertEquals(21608, totalSites, "total site count in reduced CSV")
        assertEquals(6196, proteinCount, "unique protein count in reduced CSV")
    }

    @Test
    void testParseReducedFirstAndLastEntry() {
        ExplicitSitesIndex index = AhojUbsSiteParser.parse(REDUCED_CSV)

        // First entry: A0A009I821
        List<ExplicitSitesIndex.SiteDef> firstDefs = index.getDefsForProtein("AF-A0A009I821-F1-model_v6.cif.gz")
        assertFalse(firstDefs.isEmpty(), "should find sites for first protein")

        ExplicitSitesIndex.SiteDef first = firstDefs[0]
        assertEquals("A0A009I821:ST_LJ100:1", first.siteId)
        assertEquals("AF-A0A009I821-F1-model_v6.cif.gz", first.filename)
        assertEquals(7, first.residueIds.size())
        assertEquals(10.83, first.centerX, DELTA)
        assertEquals(-2.461, first.centerY, DELTA)
        assertEquals(-29.63, first.centerZ, DELTA)

        // Last entry: X5MEI1
        List<ExplicitSitesIndex.SiteDef> lastDefs = index.getDefsForProtein("AF-X5MEI1-F1-model_v6.cif.gz")
        assertFalse(lastDefs.isEmpty(), "should find sites for last protein")

        ExplicitSitesIndex.SiteDef last = lastDefs.last()
        assertEquals("X5MEI1:ST_LJ057:4", last.siteId)
        assertEquals(6, last.residueIds.size())
    }

    // --- Full CSV tests ---

    @Test
    void testParseFullCsv() {
        ExplicitSitesIndex index = AhojUbsSiteParser.parse(FULL_CSV)

        // The full CSV has 59 columns in a different order and quoted fields with commas.
        // Parsing should work identically thanks to column-name-based access.
        int totalSites = 0
        int proteinCount = 0
        index.@byFilename.each { String filename, List<ExplicitSitesIndex.SiteDef> defs ->
            totalSites += defs.size()
            proteinCount++
        }

        assertEquals(81685, totalSites, "total site count in full CSV")
        assertEquals(9908, proteinCount, "unique protein count in full CSV")

        // Verify first entry matches expected values
        List<ExplicitSitesIndex.SiteDef> defs = index.getDefsForProtein("AF-A0A009I821-F1-model_v6.cif.gz")
        assertFalse(defs.isEmpty())

        ExplicitSitesIndex.SiteDef first = defs[0]
        assertEquals("A0A009I821:ST_100:1", first.siteId)
        assertEquals(7, first.residueIds.size())
        assertEquals(10.83, first.centerX, DELTA)
        assertEquals(-2.461, first.centerY, DELTA)
        assertEquals(-29.63, first.centerZ, DELTA)
    }

    // --- Multi-site and detail tests ---

    @Test
    void testMultipleSitesPerProtein() {
        ExplicitSitesIndex index = AhojUbsSiteParser.parse(REDUCED_CSV)

        // A0A023I7E1 has 11 distinct sites in the reduced CSV
        List<ExplicitSitesIndex.SiteDef> defs = index.getDefsForProtein("AF-A0A023I7E1-F1-model_v6.cif.gz")
        assertEquals(11, defs.size(), "A0A023I7E1 should have 11 sites")

        // All site IDs should be distinct
        Set<String> siteIds = defs.collect { it.siteId } as Set
        assertEquals(11, siteIds.size(), "all site IDs should be unique")
    }

    @Test
    void testSiteDefResidueIdsParsedCorrectly() {
        ExplicitSitesIndex index = AhojUbsSiteParser.parse(REDUCED_CSV)

        // Verify the exact residue IDs for the first entry
        ExplicitSitesIndex.SiteDef sd = index.getDefsForProtein("AF-A0A009I821-F1-model_v6.cif.gz")[0]

        List<String> expectedResidues = ["A_85", "A_90", "A_91", "A_92", "A_93", "A_94", "A_95"]
        assertEquals(expectedResidues, sd.residueIds, "residue IDs should be split on whitespace")
    }

    // --- AhojSiteInfo tests ---

    @Test
    void testFullCsvParsesAhojSiteInfo() {
        ExplicitSitesIndex index = AhojUbsSiteParser.parse(FULL_CSV)

        // First entry: A0A009I821 with known pocket metadata
        ExplicitSitesIndex.SiteDef sd = index.getDefsForProtein("AF-A0A009I821-F1-model_v6.cif.gz")[0]
        assertNotNull(sd.ahojSiteInfo, "full CSV should produce AhojSiteInfo")

        AhojSiteInfo info = sd.ahojSiteInfo
        assertEquals(1, info.nUnpPockets)
        assertEquals(1, info.nUnpPocketsMultichain)
        assertEquals("apo", info.pocketClass)
        assertEquals(0.46,  info.pocketDensityCombined,  DELTA)
        assertEquals(0.47,  info.pocketDensityPair,      DELTA)
        assertEquals(0.49,  info.pocketDensityStrongest,  DELTA)
        assertEquals(0.72,  info.pocketOverlapMode,      DELTA)
        assertEquals(0.61,  info.pocketOverlapOverall,   DELTA)
        assertEquals(0.66,  info.pocketOverlapPair,      DELTA)
        assertEquals(0.34,  info.pocketSeparationPair,   DELTA)
        assertEquals(0.821, info.pocketPApo,             DELTA)
        assertEquals(0.179, info.pocketPHolo,            DELTA)
        assertTrue(Double.isNaN(info.pocketScore), "pocket_score is empty for this row")
        assertEquals(97.54, info.modelPocketPlddt,       DELTA)
        assertEquals(10.0,  info.nApoAvg,                DELTA)
        assertEquals(2.0,   info.nHoloAvg,               DELTA)
        assertEquals(7.354, info.rg,                     DELTA)
    }

    @Test
    void testReducedCsvHasNullAhojSiteInfo() {
        ExplicitSitesIndex index = AhojUbsSiteParser.parse(REDUCED_CSV)

        // Reduced CSV has no pocket metadata columns
        ExplicitSitesIndex.SiteDef sd = index.getDefsForProtein("AF-A0A009I821-F1-model_v6.cif.gz")[0]
        assertNull(sd.ahojSiteInfo, "reduced CSV should not produce AhojSiteInfo")
    }

    @Test
    void testAhojSiteInfoHoloEntry() {
        ExplicitSitesIndex index = AhojUbsSiteParser.parse(FULL_CSV)

        // A0A010 has holo pockets — verify a non-apo entry
        List<ExplicitSitesIndex.SiteDef> defs = index.getDefsForProtein("AF-A0A010-F1-model_v6.cif.gz")
        assertFalse(defs.isEmpty())

        AhojSiteInfo info = defs[0].ahojSiteInfo
        assertNotNull(info)
        assertEquals("holo", info.pocketClass)
    }

    // --- Partial-format / missing-column test ---

    /**
     * Older "full"-ish CSVs may have {@code pocket_class} (the marker that
     * triggers AhojSiteInfo attachment) but lack newer columns like {@code rg}
     * and {@code n_unp_pockets*}. fromCsvRecord must degrade missing fields
     * to per-type sentinels rather than throwing.
     */
    @Test
    void partialFullFormatCsvParsesWithoutThrowing(@TempDir Path tmp) {
        Path csv = tmp.resolve("partial.csv")
        Files.writeString(csv,
            "site_uid,afdb_filename,chain_resi,center_x,center_y,center_z,pocket_class,pocket_score\n" +
            "s1,A.cif.gz,A_42,1.0,2.0,3.0,apo,0.75\n")

        ExplicitSitesIndex index = AhojUbsSiteParser.parse(csv.toString())
        ExplicitSitesIndex.SiteDef sd = index.getDefsForProtein("A.cif.gz")[0]

        assertNotNull(sd.ahojSiteInfo)
        assertEquals("apo", sd.ahojSiteInfo.pocketClass)
        assertEquals(0.75d, sd.ahojSiteInfo.pocketScore, DELTA)
        assertTrue(Double.isNaN(sd.ahojSiteInfo.rg), "missing rg column should be NaN, not throw")
        assertEquals(0, sd.ahojSiteInfo.nUnpPockets, "missing n_unp_pockets should be 0")
    }

    // --- Cross-format consistency test ---

    @Test
    void testBothFormatsProduceSameResultForSharedEntry() {
        // A0A009I821 appears in both CSVs with the same residues and coordinates.
        // The site_uid differs (ST_LJ vs ST_100), but structural data should match.
        ExplicitSitesIndex reducedIndex = AhojUbsSiteParser.parse(REDUCED_CSV)
        ExplicitSitesIndex fullIndex = AhojUbsSiteParser.parse(FULL_CSV)

        String protein = "AF-A0A009I821-F1-model_v6.cif.gz"
        ExplicitSitesIndex.SiteDef reduced = reducedIndex.getDefsForProtein(protein)[0]
        ExplicitSitesIndex.SiteDef full = fullIndex.getDefsForProtein(protein)[0]

        // Same filename
        assertEquals(reduced.filename, full.filename)

        // Same residue IDs and coordinates
        assertEquals(reduced.residueIds, full.residueIds, "residue IDs should match across formats")
        assertEquals(reduced.centerX, full.centerX, DELTA, "center X should match")
        assertEquals(reduced.centerY, full.centerY, DELTA, "center Y should match")
        assertEquals(reduced.centerZ, full.centerZ, DELTA, "center Z should match")
    }

}
