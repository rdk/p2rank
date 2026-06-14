package cz.siret.prank.domain.loaders

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.ResidueSite
import cz.siret.prank.geom.Point
import cz.siret.prank.program.PrankException
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.ResidueNumber

import javax.annotation.Nullable

/**
 * Index of explicit site definitions loaded from an external CSV file.
 * Keyed by filename for O(1) lookup per protein.
 * Pluggable via format string dispatched in {@link #loadFromFile}.
 */
@Slf4j
@CompileStatic
class ExplicitSitesIndex {

    @CompileStatic
    static class SiteDef {
        String siteId
        String filename
        List<String> residueIds
        double centerX
        double centerY
        double centerZ
        @Nullable AhojSiteInfo ahojSiteInfo

        SiteDef(String siteId, String filename, List<String> residueIds,
                double centerX, double centerY, double centerZ,
                @Nullable AhojSiteInfo ahojSiteInfo = null) {
            this.siteId = siteId
            this.filename = filename
            this.residueIds = residueIds
            this.centerX = centerX
            this.centerY = centerY
            this.centerZ = centerZ
            this.ahojSiteInfo = ahojSiteInfo
        }
    }

    private final Map<String, List<SiteDef>> byFilename

    ExplicitSitesIndex(Map<String, List<SiteDef>> byFilename) {
        this.byFilename = byFilename
    }

    static ExplicitSitesIndex loadFromFile(String format, String filePath) {
        switch (format) {
            case "ahoj_ubs":
                return AhojUbsSiteParser.parse(filePath)
            default:
                throw new PrankException("Unknown explicit sites format: " + format)
        }
    }

    List<SiteDef> getDefsForProtein(String proteinFile) {
        String filename = Futils.shortName(proteinFile)
        List<SiteDef> defs = byFilename.get(filename)
        return defs != null ? defs : Collections.<SiteDef> emptyList()
    }

    List<ResidueSite> resolveForProtein(Protein protein, String proteinFile) {
        List<SiteDef> defs = getDefsForProtein(proteinFile)
        if (defs.isEmpty()) {
            log.warn "No explicit sites found for [{}] in sites file", Futils.shortName(proteinFile)
            return Collections.<ResidueSite> emptyList()
        }

        List<ResidueSite> sites = new ArrayList<>()
        for (SiteDef sd : defs) {
            List<Residue> residues = resolveResidues(sd, protein)
            if (residues.isEmpty()) {
                log.warn "Site [{}] has no resolved residues, skipping", sd.siteId
                continue
            }
            Atom centroid = Point.of(sd.centerX, sd.centerY, sd.centerZ)
            ResidueSite rs = new ResidueSite(sd.siteId, centroid, residues, protein)
            if (sd.ahojSiteInfo != null) {
                rs.secondaryData.put(ResidueSite.KEY_AHOJ_SITE_INFO, sd.ahojSiteInfo)
            }
            sites.add(rs)
        }
        return sites
    }

    private List<Residue> resolveResidues(SiteDef sd, Protein protein) {
        List<Residue> resolved = new ArrayList<>()
        for (String resId : sd.residueIds) {
            ResidueNumber rn
            try {
                rn = ExtendedResidueId.parse(resId).toResidueNumber()
            } catch (RuntimeException e) {
                // Malformed token in a third-party CSV: warn and skip, consistent with the
                // well-formed-but-unresolvable case below (don't abort the whole item load).
                log.warn "Cannot parse residue id [{}] for site [{}] in protein [{}]: {}",
                        resId, sd.siteId, protein.name, e.message
                continue
            }
            Residue r = protein.residues.getResidue(Residue.Key.of(rn))
            if (r != null) {
                resolved.add(r)
            } else {
                log.warn "Cannot resolve residue [{}] for site [{}] in protein [{}]",
                        resId, sd.siteId, protein.name
            }
        }
        return resolved
    }

}
