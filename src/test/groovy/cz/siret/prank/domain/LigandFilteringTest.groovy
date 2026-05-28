package cz.siret.prank.domain

import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.program.params.Params
import org.junit.jupiter.api.*
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests for ligand categorization thresholds and filtering behavior.
 */
@Isolated
@ResourceLock("Params")
class LigandFilteringTest {

    static final String TEST_PROTEIN = "distro/test_data/2W83.pdb"

    static Params originalParams
    static boolean origIgnoreLigandsSwitch

    @BeforeAll
    static void setup() {
        originalParams = (Params) Params.inst.clone()
        origIgnoreLigandsSwitch = LoaderParams.ignoreLigandsSwitch
        LoaderParams.ignoreLigandsSwitch = false
    }

    @AfterAll
    static void tearDown() {
        Params.INSTANCE = originalParams
        LoaderParams.ignoreLigandsSwitch = origIgnoreLigandsSwitch
    }

    @BeforeEach
    void resetParams() {
        Params.INSTANCE = new Params()
        LoaderParams.ignoreLigandsSwitch = false
    }

    // ===== Basic ligand loading =====

    @Test
    void proteinHasLigands() {
        Protein protein = Protein.load(TEST_PROTEIN, new LoaderParams(ignoreLigands: false))

        int totalLigands = protein.ligands.allIncludingIgnored.size()
        assertTrue(totalLigands > 0,
                "2W83 should have at least one ligand (relevant or ignored)")
    }

    @Test
    void ignoredLigandsContainHetGroups() {
        Protein protein = Protein.load(TEST_PROTEIN, new LoaderParams(ignoreLigands: false))

        // The default ignore_het_groups list includes HOH and other common groups.
        // ignoredLigands are those whose group name is in ignore_het_groups.
        List<Ligand> ignored = protein.ligands.ignoredLigands
        // 2W83 contains water molecules (HOH) which should be in the ignored list
        assertFalse(ignored.isEmpty(), "2W83 should have ignored ligands (e.g. HOH)")

        Set<String> defaultIgnored = Params.inst.ignore_het_groups as Set
        boolean anyInDefaultList = ignored.any { defaultIgnored.contains(it.name) }
        assertTrue(anyInDefaultList,
                "at least one ignored ligand should have a name from default ignore_het_groups")
    }

    // ===== min_ligand_atoms threshold =====

    @Test
    void highMinLigandAtomsMovesLigandsToSmall() {
        // Load with default min_ligand_atoms (5)
        Protein proteinDefault = Protein.load(TEST_PROTEIN, new LoaderParams(ignoreLigands: false))
        int defaultRelevant = proteinDefault.ligands.relevantLigands.size()
        int defaultSmall = proteinDefault.ligands.smallLigands.size()

        // Now set a very high threshold so most/all ligands are "too small"
        Params.INSTANCE = new Params()
        Params.inst.min_ligand_atoms = 9999

        Protein proteinStrict = Protein.load(TEST_PROTEIN, new LoaderParams(ignoreLigands: false))
        int strictRelevant = proteinStrict.ligands.relevantLigands.size()
        int strictSmall = proteinStrict.ligands.smallLigands.size()

        assertTrue(strictRelevant <= defaultRelevant,
                "raising min_ligand_atoms should not increase relevant ligands")
        assertTrue(strictSmall >= defaultSmall,
                "raising min_ligand_atoms should not decrease small ligands")
    }

    @Test
    void zeroMinLigandAtomsAcceptsAllBySize() {
        Params.inst.min_ligand_atoms = 0

        Protein protein = Protein.load(TEST_PROTEIN, new LoaderParams(ignoreLigands: false))

        // With min_ligand_atoms=0, no ligand should be rejected for being "too small"
        assertEquals(0, protein.ligands.smallLigands.size(),
                "no ligands should be categorized as small when min_ligand_atoms=0")
    }

    // ===== ignoreLigands flag =====

    @Test
    void ignoreLigandsFlagSkipsLigandLoading() {
        Protein protein = Protein.load(TEST_PROTEIN, new LoaderParams(ignoreLigands: true))

        assertEquals(0, protein.ligands.relevantLigands.size(),
                "no relevant ligands should be loaded when ignoreLigands=true")
        assertEquals(0, protein.ligands.ignoredLigands.size(),
                "no ignored ligands should be loaded when ignoreLigands=true")
        assertEquals(0, protein.ligands.smallLigands.size(),
                "no small ligands should be loaded when ignoreLigands=true")
    }

}
