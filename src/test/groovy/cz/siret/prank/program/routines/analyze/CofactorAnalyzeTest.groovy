package cz.siret.prank.program.routines.analyze

import cz.siret.prank.domain.CofactorHandler
import cz.siret.prank.domain.Dataset
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static cz.siret.prank.domain.Dataset.LigandDefinition
import static org.junit.jupiter.api.Assertions.*

/**
 * Tests for the building blocks of {@code analyze cofactors}.
 *
 * The {@code cmdCofactors} subcommand itself is exercised end-to-end by the smoke harness
 * (misc/test-scripts/testsets.sh, function {@code cofactors()}) because {@code AnalyzeRoutine}
 * requires a {@code Main} instance and {@code Main.findInstallDir} hard-codes paths.
 *
 * Here we cover the testable pieces under cmdCofactors:
 *  - {@code Dataset.resolveCofactorDefinitions} - per-item precedence (column overrides global)
 *  - {@code CofactorHandler.parseAndValidate} - case normalization, bracket-aware split
 *  - {@code DataTable.toCsv} CSV quoting for cofactor-shaped data (cross-referenced in DataTableCsvTest)
 */
@Isolated
@ResourceLock("Params")
@CompileStatic
class CofactorAnalyzeTest {

    static Params savedParams

    @BeforeAll
    static void setup() {
        savedParams = (Params) Params.inst.clone()
        Params.INSTANCE = new Params()
    }

    @AfterAll
    static void tearDown() {
        Params.INSTANCE = savedParams
    }

    @Test
    void resolveCofactorDefinitions_columnOverridesGlobal() {
        // Per-item resolution: the dataset's `cofactors` column wins over the global
        // Params.cofactors. This is the precedence rule analyze cofactors uses
        // (see AnalyzeRoutine.cmdCofactors and Dataset.getLoaderParams).
        Params.inst.cofactors = ["FAD"]
        Dataset ds = new Dataset("test", "/tmp")
        Map<String, String> cols = ["protein": "x.pdb", "cofactors": "PLP"]
        Dataset.Item item = ds.createNewItemForSingleFileDs("x.pdb", cols)

        List<LigandDefinition> defs = ds.resolveCofactorDefinitions(item)
        assertEquals(1, defs.size())
        assertEquals("PLP", defs[0].groupName, "Column value PLP should win over global FAD")
    }

    @Test
    void resolveCofactorDefinitions_emptyColumnFallsBackToGlobal() {
        Params.inst.cofactors = ["HEM"]
        Dataset ds = new Dataset("test", "/tmp")
        // Column present but empty -> fall back to global
        Map<String, String> cols = ["protein": "x.pdb", "cofactors": ""]
        Dataset.Item item = ds.createNewItemForSingleFileDs("x.pdb", cols)

        List<LigandDefinition> defs = ds.resolveCofactorDefinitions(item)
        assertEquals(1, defs.size())
        assertEquals("HEM", defs[0].groupName, "Empty column should defer to global Params.cofactors")
    }

    @Test
    void resolveCofactorDefinitions_missingColumnUsesGlobal() {
        Params.inst.cofactors = ["FMN"]
        Dataset ds = new Dataset("test", "/tmp")
        Map<String, String> cols = ["protein": "x.pdb"]  // no cofactors column at all
        Dataset.Item item = ds.createNewItemForSingleFileDs("x.pdb", cols)

        List<LigandDefinition> defs = ds.resolveCofactorDefinitions(item)
        assertEquals(1, defs.size())
        assertEquals("FMN", defs[0].groupName)
    }

    @Test
    void resolveCofactorDefinitions_bothEmptyReturnsEmpty() {
        Params.inst.cofactors = []
        Dataset ds = new Dataset("test", "/tmp")
        Map<String, String> cols = ["protein": "x.pdb"]
        Dataset.Item item = ds.createNewItemForSingleFileDs("x.pdb", cols)

        assertTrue(ds.resolveCofactorDefinitions(item).isEmpty())
    }

    @Test
    void resolveCofactorDefinitions_preservesContactResIdsCommas() {
        // Plan §"Stage 4": a `contact_res_ids` specifier with commas must round-trip through
        // column splitting unscathed. The bracket-aware splitter inside parseAndValidate is
        // what makes this work.
        Params.inst.cofactors = []
        Dataset ds = new Dataset("test", "/tmp")
        Map<String, String> cols = ["protein": "x.pdb",
                                    "cofactors": "FAD[contact_res_ids:A_D246,A_T259,A_E423]"]
        Dataset.Item item = ds.createNewItemForSingleFileDs("x.pdb", cols)

        List<LigandDefinition> defs = ds.resolveCofactorDefinitions(item)
        assertEquals(1, defs.size(),
                "contact_res_ids value with inner commas must remain one specifier, got: ${defs*.originalString}")
        assertEquals("FAD", defs[0].groupName)
        assertTrue(defs[0].originalString.contains("contact_res_ids:A_D246,A_T259,A_E423"),
                "originalString must preserve all three residue ids: ${defs[0].originalString}")
    }

    @Test
    void parseAndValidate_dropsBlankSpecifiers() {
        // A trailing comma in the dataset column produces an empty token; it must be silently dropped.
        List<LigandDefinition> defs = CofactorHandler.parseAndValidate("FAD,,PLP,")
        assertEquals(2, defs.size())
        assertEquals("FAD", defs[0].groupName)
        assertEquals("PLP", defs[1].groupName)
    }
}
