package cz.siret.prank.domain.loaders

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.loaders.ExplicitSitesIndex.SiteDef
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Regression for ExplicitSitesIndex residue-token robustness.
 *
 * A malformed residue token in a third-party (AHoJ-UBS) sites CSV must be
 * warn-and-skipped, mirroring the existing handling for well-formed-but-
 * unresolvable residues — not throw and abort the entire dataset-item load.
 */
class ExplicitSitesIndexTest {

    @Test
    void resolveForProteinSkipsMalformedResidueTokensInsteadOfCrashing() {
        // "NOT_A_RESID" (3 underscore-fields) and "garbage" (no separator) both fail
        // ExtendedResidueId.parse with a PrankException.
        SiteDef sd = new SiteDef("site1", "prot.pdb", ["NOT_A_RESID", "garbage"], 1d, 2d, 3d)
        ExplicitSitesIndex index = new ExplicitSitesIndex(["prot.pdb": [sd]])

        Protein protein = new Protein()
        protein.name = "prot"

        // Before the fix this threw PrankException out of resolveResidues.
        List<?> sites = index.resolveForProtein(protein, "some/dir/prot.pdb")

        assertTrue(sites.isEmpty(),
                "a site whose residue tokens are all unparseable must be skipped, not crash the load")
    }
}
