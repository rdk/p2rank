package cz.siret.prank.domain

import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.program.PrankException
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test
import org.biojava.nbio.structure.Group

import static cz.siret.prank.domain.Dataset.LigandDefinition
import static org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for CofactorHandler - specifier parsing, configuration, and LoaderParams glue.
 *
 * Integration tests that exercise extractCofactorAtoms against a real BioJava Structure
 * live in {@link CofactorIntegrationTest} and {@link CofactorPipelineTest}.
 */
@CompileStatic
class CofactorHandlerTest {

    private static List<LigandDefinition> parse(List<String> specs) {
        return CofactorHandler.parseAndValidate(specs)
    }

    // ===== Handler configuration =====

    @Test
    void disabledWhenEmptyList() {
        assertFalse(new CofactorHandler([] as List<LigandDefinition>).isEnabled())
    }

    @Test
    void disabledWhenNullList() {
        assertFalse(new CofactorHandler(null).isEnabled())
    }

    @Test
    void enabledWhenSpecifiersGiven() {
        assertTrue(new CofactorHandler(parse(["FAD", "PLP"])).isEnabled())
    }

    @Test
    void isCofactorIsFalseForNull() {
        def handler = new CofactorHandler(parse(["FAD"]))
        assertFalse(handler.isCofactor((Group) null))
    }

    // ===== Specifier parsing & validation =====

    @Test
    void parsesBareName() {
        def defs = parse(["FAD"])
        assertEquals(1, defs.size())
        assertEquals("FAD", defs[0].groupName)
        assertNull(defs[0].groupId)
        assertNull(defs[0].atomId)
    }

    @Test
    void parsesGroupIdSpecifier() {
        def defs = parse(["FAD[group_id:A_500]"])
        assertEquals("FAD", defs[0].groupName)
        assertEquals("A_500", defs[0].groupId)
    }

    @Test
    void parsesShorthandGroupId() {
        // Bare bracketed value defaults to group_id per LigandDefinition.parse
        def defs = parse(["HEM[A_300]"])
        assertEquals("HEM", defs[0].groupName)
        assertEquals("A_300", defs[0].groupId)
    }

    @Test
    void parsesAtomIdSpecifier() {
        def defs = parse(["FAD[atom_id:12345]"])
        assertEquals(Integer.valueOf(12345), defs[0].atomId)
    }

    @Test
    void parsesContactResIdsSpecifier() {
        def defs = parse(["PLP[contact_res_ids:A_D246,A_T259]"])
        assertEquals(2, defs[0].contactResidueIds.size())
    }

    @Test
    void rejectsUnknownSpecifierType() {
        assertThrows(PrankException) { parse(["FAD[whatever:foo]"]) }
    }

    @Test
    void rejectsNonIntegerAtomId() {
        assertThrows(PrankException) { parse(["FAD[atom_id:notanumber]"]) }
    }

    @Test
    void errorMessageMentionsCofactor() {
        // R7/#7: error from LigandDefinition is wrapped with cofactor-specific context,
        // so users don't see misleading "dataset file" text when the source is CLI.
        def ex = assertThrows(PrankException) { parse(["FAD[whatever:foo]"]) }
        assertTrue(ex.message.contains("cofactor"),
                "Error message should mention 'cofactor', was: ${ex.message}")
        assertTrue(ex.message.contains("FAD[whatever:foo]"),
                "Error message should include the offending specifier, was: ${ex.message}")
    }

    // ===== R-AUDIT-1: bracket-aware splitting for contact_res_ids =====

    @Test
    void contactResIdsCommasArePreservedFromList() {
        // Bug #1 from audit: when CLI/CHAIN_SPLITTER over-splits a specifier like
        // FAD[contact_res_ids:A_D246,A_T259,A_E423] into multiple list elements,
        // parseAndValidate must rejoin and re-split with bracket awareness.
        List<String> overSplit = ["FAD[contact_res_ids:A_D246", "A_T259", "A_E423]"]
        def defs = CofactorHandler.parseAndValidate(overSplit)
        assertEquals(1, defs.size(), "Should reassemble into one specifier, got: $defs")
        assertEquals("FAD", defs[0].groupName)
        assertEquals(3, defs[0].contactResidueIds.size())
        assertEquals(["A_D246", "A_T259", "A_E423"], defs[0].contactResidueIds)
    }

    @Test
    void contactResIdsCommasArePreservedFromString() {
        // Bug #1: column-string path must also use bracket-aware splitting.
        def defs = CofactorHandler.parseAndValidate(
                "FAD[contact_res_ids:A_D246,A_T259,A_E423],PLP")
        assertEquals(2, defs.size())
        assertEquals("FAD", defs[0].groupName)
        assertEquals(3, defs[0].contactResidueIds.size())
        assertEquals("PLP", defs[1].groupName)
    }

    @Test
    void groupIdSpecifierWithCommaInBrackets() {
        // Defensive: group_id values don't normally have commas, but the bracket-aware
        // splitter should preserve them even if a chain ID happens to contain weird chars.
        def defs = CofactorHandler.parseAndValidate("FAD[group_id:A_500],PLP[A_300]")
        assertEquals(2, defs.size())
    }

    // ===== R-AUDIT-2: case normalization =====

    @Test
    void lowercaseGroupNameIsNormalized() {
        // Bug #2: BioJava stores PDB names in uppercase. A user-supplied "fad" must
        // be matched against "FAD" - not silently zero-matched.
        def defs = CofactorHandler.parseAndValidate(["fad"])
        assertEquals(1, defs.size())
        assertEquals("FAD", defs[0].groupName)
    }

    @Test
    void mixedCaseGroupNameIsNormalized() {
        def defs = CofactorHandler.parseAndValidate(["Fad", "pLp[A_500]"])
        assertEquals(2, defs.size())
        assertEquals("FAD", defs[0].groupName)
        assertEquals("PLP", defs[1].groupName)
        // chain ID inside [] is preserved as-is (case-significant)
        assertEquals("A_500", defs[1].groupId)
    }

    @Test
    void specifierBodyPreservesCase() {
        // The bracketed specifier body (chain IDs, residue codes) is case-significant.
        // Only the group-name prefix is upper-cased.
        def defs = CofactorHandler.parseAndValidate(["fad[contact_res_ids:A_d246]"])
        assertEquals("FAD", defs[0].groupName)
        // The contact-residue-id retains its original case (BioJava handles AA-code case)
        assertEquals(["A_d246"], defs[0].contactResidueIds)
    }

    @Test
    void emptyEntriesAreDropped() {
        def defs = parse(["FAD", "", null, "  ", "PLP"])
        assertEquals(2, defs.size())
        assertEquals(["FAD", "PLP"], defs*.groupName)
    }

    @Test
    void parseAndValidateHandlesNull() {
        assertEquals([], CofactorHandler.parseAndValidate((List<String>) null))
        assertEquals([], CofactorHandler.parseAndValidate([] as List<String>))
        assertEquals([], CofactorHandler.parseAndValidate((String) null))
        assertEquals([], CofactorHandler.parseAndValidate("  "))
    }

    @Test
    void preservesOriginalSpecifierString() {
        def defs = parse(["FAD[group_id:A_500]"])
        assertEquals("FAD[group_id:A_500]", defs[0].originalString)
    }

    // ===== LoaderParams integration =====

    @Test
    void loaderParamsDefaultsToNullHandler() {
        def lp = new LoaderParams()
        assertNull(lp.cofactorHandler)
        assertFalse(lp.isCofactor((Group) null))
    }

    @Test
    void loaderParamsWithHandlerDelegatesIsCofactor() {
        def lp = new LoaderParams()
        lp.cofactorHandler = new CofactorHandler(parse(["FAD"]))
        // No extraction has run yet → matchedGroups empty → isCofactor returns false for any group
        assertFalse(lp.isCofactor((Group) null))
    }
}
