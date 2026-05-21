package cz.siret.prank.domain

import cz.siret.prank.domain.loaders.LoaderParams
import groovy.transform.CompileStatic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.function.ThrowingSupplier
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static cz.siret.prank.domain.Dataset.LigandDefinition
import static org.junit.jupiter.api.Assertions.*

/**
 * Integration tests for the cofactor feature against real BioJava structures.
 *
 * Uses:
 *  - {@code 1AHP.pdb} (PLP cofactor) - gated behind @EnabledIf("has1AHP") so the test
 *    is skipped on environments that haven't downloaded the file.
 *  - {@code 1fbl.pdb} (no relevant cofactor) - for baseline / regression checks. Always available.
 */
@Isolated
@ResourceLock("Params")
@CompileStatic
class CofactorIntegrationTest {

    static final String TEST_DATA = "distro/test_data"
    static final String PDB_1AHP = "$TEST_DATA/1AHP.pdb"
    static final String PDB_1FBL = "$TEST_DATA/1fbl.pdb"

    static boolean savedIgnoreLigandsSwitch

    static boolean has1AHP() {
        return new File(PDB_1AHP).exists()
    }

    private static LoaderParams loaderParamsWithCofactors(List<String> specs) {
        def lp = new LoaderParams()
        if (specs != null && !specs.isEmpty()) {
            lp.cofactorHandler = new CofactorHandler(CofactorHandler.parseAndValidate(specs))
        }
        return lp
    }

    @BeforeAll
    static void setup() {
        savedIgnoreLigandsSwitch = LoaderParams.ignoreLigandsSwitch
        LoaderParams.ignoreLigandsSwitch = false
    }

    @AfterAll
    static void tearDown() {
        LoaderParams.ignoreLigandsSwitch = savedIgnoreLigandsSwitch
    }

    // ===== Baseline / default behaviour =====

    @Test
    void defaultBehaviorUnchanged() {
        def lp = new LoaderParams()
        def protein = Protein.load(PDB_1FBL, lp)

        assertNotNull(protein.proteinAtoms)
        assertFalse(protein.proteinAtoms.empty)
        assertTrue(protein.proteinAtoms.count > 2000)
    }

    @Test
    void emptyCofactorListSameAsDefault() {
        def lp1 = new LoaderParams()
        def lp2 = new LoaderParams()
        lp2.cofactorHandler = new CofactorHandler([] as List<LigandDefinition>)

        def p1 = Protein.load(PDB_1FBL, lp1)
        def p2 = Protein.load(PDB_1FBL, lp2)

        assertEquals(p1.proteinAtoms.count, p2.proteinAtoms.count)
    }

    @Test
    void missingCofactorDoesNotFail() {
        // "ZZZZ" is a valid specifier syntax that won't match any real PDB residue name
        def lp = loaderParamsWithCofactors(["ZZZZ"])
        ThrowingSupplier<Protein> supplier = { Protein.load(PDB_1FBL, lp) } as ThrowingSupplier<Protein>
        Protein protein = assertDoesNotThrow(supplier)
        assertNotNull(protein.proteinAtoms)
        assertEquals(["ZZZZ"], protein.cofactorExtractionResult?.unmatchedSpecifiers)
    }

    // ===== 1AHP (PLP) =====

    @Test
    @EnabledIf("has1AHP")
    void cofactorAtomsContributeToProteinSurface() {
        def p1 = Protein.load(PDB_1AHP, new LoaderParams())
        int atomsWithout = p1.proteinAtoms.count

        def p2 = Protein.load(PDB_1AHP, loaderParamsWithCofactors(["PLP"]))
        int atomsWith = p2.proteinAtoms.count

        assertTrue(atomsWith > atomsWithout,
                "Cofactor atoms should be added: before=$atomsWithout after=$atomsWith")
        assertTrue(atomsWith - atomsWithout >= 10,
                "Expected ~15 PLP heavy atoms, got ${atomsWith - atomsWithout}")
    }

    @Test
    @EnabledIf("has1AHP")
    void cofactorIsNotMovedToIgnoredWhenLoadingFromSeparateFiles() {
        // Default branch already filters cofactors out of getLigandGroups; the
        // separate-files branch in Ligands.loadLigandsFromSeparateFiles must do
        // the same so a cofactor doesn't surface as an ignored Ligand.
        def lp = loaderParamsWithCofactors(["PLP"])
        lp.loadLigandsFromSeparateFiles = true

        Protein protein = Protein.load(PDB_1AHP, lp)

        assertFalse(protein.ligands.ignoredLigands*.name.any { ((String) it).contains("PLP") },
                "PLP should be filtered out of ignoredLigands in separate-files mode")
    }

    @Test
    @EnabledIf("has1AHP")
    void cofactorsExcludedFromLigandDetection() {
        def protein = Protein.load(PDB_1AHP, loaderParamsWithCofactors(["PLP"]))

        def allLigandNames = protein.ligands.allIncludingIgnored
                .collectMany { [it.name, it.nameCode] }
                .findAll { it != null }

        assertFalse(allLigandNames.any { ((String) it).toUpperCase().contains("PLP") },
                "PLP should be excluded from ligand detection, found: $allLigandNames")
    }

    @Test
    @EnabledIf("has1AHP")
    void extractionResultIsAccessibleOnProtein() {
        def protein = Protein.load(PDB_1AHP, loaderParamsWithCofactors(["PLP"]))

        def result = protein.cofactorExtractionResult
        assertNotNull(result, "ExtractionResult should be stored on Protein")
        assertFalse(result.atoms.empty, "PLP atoms should be in result")
        assertTrue(result.foundGroups.containsKey("PLP"), "PLP should be in foundGroups")
        assertTrue(result.unmatchedSpecifiers.isEmpty(), "Specifier should have matched")
    }

    // ===== Precise specifiers (R16) =====

    @Test
    @EnabledIf("has1AHP")
    void preciseGroupIdMatchesNoMoreThanBareName() {
        def proteinAll = Protein.load(PDB_1AHP, loaderParamsWithCofactors(["PLP"]))
        int allMatched = proteinAll.cofactorExtractionResult.foundGroups.get("PLP")?.size() ?: 0

        // A non-existent precise specifier should match strictly less (probably zero)
        def proteinNone = Protein.load(PDB_1AHP, loaderParamsWithCofactors(["PLP[Z_999]"]))
        int noneMatched = proteinNone.cofactorExtractionResult?.foundGroups?.get("PLP")?.size() ?: 0

        assertTrue(noneMatched <= allMatched,
                "Precise specifier should match no more than the bare name (all=$allMatched, precise=$noneMatched)")
        assertEquals(0, noneMatched, "Z_999 does not exist in 1AHP")
    }

    @Test
    void unmatchedPreciseSpecifierIsReported() {
        // FAD[Z_999] won't match anything in 1fbl. The handler should report it as unmatched.
        def lp = loaderParamsWithCofactors(["FAD[group_id:Z_999]"])
        ThrowingSupplier<Protein> supplier = { Protein.load(PDB_1FBL, lp) } as ThrowingSupplier<Protein>
        Protein protein = assertDoesNotThrow(supplier)
        assertNotNull(protein.proteinAtoms)
        assertEquals(["FAD[group_id:Z_999]"],
                protein.cofactorExtractionResult?.unmatchedSpecifiers)
    }
}
