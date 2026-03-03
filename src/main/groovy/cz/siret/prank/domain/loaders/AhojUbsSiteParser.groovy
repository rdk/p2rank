package cz.siret.prank.domain.loaders

import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils

/**
 * Parses the 'ahoj_ubs' CSV format for explicit binding site definitions.
 *
 * Expected CSV columns:
 * site_uniprots,site_uid,site_recipe,threshold,afdb_filename,chain_resi,center_x,center_y,center_z
 */
@Slf4j
@CompileStatic
class AhojUbsSiteParser {

    static ExplicitSitesIndex parse(String filePath) {
        List<String> lines = Futils.readLines(filePath)
        Map<String, List<ExplicitSitesIndex.SiteDef>> byFilename = new LinkedHashMap<>()

        int totalSites = 0
        for (String line : lines.tail()) {
            if (StringUtils.isBlank(line)) continue

            String[] cols = line.split(",", -1)

            String siteId = cols[1]
            String filename = cols[4]
            List<String> residueIds = Sutils.splitOnWhitespace(cols[5])
            double cx = Double.parseDouble(cols[6])
            double cy = Double.parseDouble(cols[7])
            double cz = Double.parseDouble(cols[8])

            ExplicitSitesIndex.SiteDef sd = new ExplicitSitesIndex.SiteDef(
                    siteId, filename, residueIds, cx, cy, cz)
            byFilename.computeIfAbsent(filename, { new ArrayList<>() }).add(sd)
            totalSites++
        }

        log.info "Loaded explicit sites index: {} sites for {} proteins from [{}]",
                totalSites, byFilename.size(), filePath

        return new ExplicitSitesIndex(byFilename)
    }

}
