package cz.siret.prank.domain.loaders

import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.apache.commons.lang3.StringUtils

import java.nio.charset.StandardCharsets

/**
 * Parses the 'ahoj_ubs' CSV format for explicit binding site definitions.
 *
 * Supports both the reduced (9-column) and full (59-column) CSV variants.
 * Columns are accessed by name, so column order does not matter.
 *
 * Required columns: site_uid, afdb_filename, chain_resi, center_x, center_y, center_z
 */
@Slf4j
@CompileStatic
class AhojUbsSiteParser {

    // Column names shared by both reduced and full CSV formats
    private static final String COL_SITE_UID   = "site_uid"
    private static final String COL_FILENAME   = "afdb_filename"
    private static final String COL_CHAIN_RESI = "chain_resi"
    private static final String COL_CENTER_X   = "center_x"
    private static final String COL_CENTER_Y   = "center_y"
    private static final String COL_CENTER_Z   = "center_z"

    /** RFC 4180 handles both quoted and unquoted fields correctly */
    private static final CSVFormat CSV_FORMAT = CSVFormat.RFC4180.withFirstRecordAsHeader()

    static ExplicitSitesIndex parse(String filePath) {
        Map<String, List<ExplicitSitesIndex.SiteDef>> byFilename = new LinkedHashMap<>()

        int totalSites = 0
        int skippedEmpty = 0

        // Futils.inputStream auto-decompresses by extension (.gz/.zst/...); plain files pass through
        Futils.inputStream(filePath).withReader(StandardCharsets.UTF_8.name()) { Reader reader ->
            CSVParser csvParser = CSV_FORMAT.parse(reader)

            // Check if the full format columns are present
            boolean hasAhojInfo = csvParser.headerNames.contains(AhojSiteInfo.MARKER_COLUMN)

            for (CSVRecord record : csvParser) {
                String chainResi = record.get(COL_CHAIN_RESI)

                // Skip rows with empty residue definitions
                if (StringUtils.isBlank(chainResi)) {
                    skippedEmpty++
                    continue
                }

                String siteId = record.get(COL_SITE_UID)
                String filename = record.get(COL_FILENAME)
                List<String> residueIds = Sutils.splitOnWhitespace(chainResi)
                double cx = Double.parseDouble(record.get(COL_CENTER_X))
                double cy = Double.parseDouble(record.get(COL_CENTER_Y))
                double cz = Double.parseDouble(record.get(COL_CENTER_Z))

                AhojSiteInfo ahojInfo = hasAhojInfo ? AhojSiteInfo.fromCsvRecord(record) : null

                ExplicitSitesIndex.SiteDef sd = new ExplicitSitesIndex.SiteDef(
                        siteId, filename, residueIds, cx, cy, cz, ahojInfo)
                byFilename.computeIfAbsent(filename, { new ArrayList<>() }).add(sd)
                totalSites++
            }
        }

        if (skippedEmpty > 0) {
            log.warn "Skipped {} rows with empty residue/coordinate fields in [{}]", skippedEmpty, filePath
        }
        log.info "Loaded explicit sites index: {} sites for {} proteins from [{}]",
                totalSites, byFilename.size(), filePath

        return new ExplicitSitesIndex(byFilename)
    }

}
