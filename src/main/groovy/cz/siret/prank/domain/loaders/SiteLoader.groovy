package cz.siret.prank.domain.loaders

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.ResidueSite
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Loads binding sites from external definition (e.g. CSV file).
 * Placeholder — actual loading logic will be implemented later.
 * Note: currently unused — Protein.sites is never populated by any code path yet.
 */
@Slf4j
@CompileStatic
class SiteLoader {

    List<ResidueSite> loadForProtein(Protein protein, String siteDefinitionFile) {
        log.info "Site loading not yet implemented for protein [{}], file [{}]", protein.name, siteDefinitionFile
        return Collections.<ResidueSite>emptyList()
    }

}
