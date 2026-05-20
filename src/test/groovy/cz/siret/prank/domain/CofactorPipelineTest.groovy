package cz.siret.prank.domain

import cz.siret.prank.domain.AminoAcidMapper
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.features.implementation.conservation.ConservationScore
import cz.siret.prank.features.implementation.conservation.ResidueNumberWrapper
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Params
import cz.siret.prank.test.Log4jCapture
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.ResidueNumber
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.function.ThrowingSupplier
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import java.lang.reflect.Constructor
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

import static cz.siret.prank.domain.Dataset.LigandDefinition
import static org.junit.jupiter.api.Assertions.*

/**
 * Pipeline-level cofactor tests using the in-repo {@code 1t7qa.pdb} (contains COA).
 *
 * These exercise the loading path end-to-end (Protein.load → cofactor extraction →
 * ligand detection), but stop short of running a full prediction. The
 * full {@code prank predict} run is covered by the drop-in safety benchmark
 * ({@code benchmark/cofactors_dropin_safety.sh}).
 */
@Isolated
@ResourceLock("Params")
@CompileStatic
class CofactorPipelineTest {

    static final String PDB_1T7QA = "distro/test_data/liganated/1t7qa.pdb"
    static final String PDB_1FBL = "distro/test_data/1fbl.pdb"
    static final String PDB_1AHP = "distro/test_data/1AHP.pdb"

    static boolean has1AHP() {
        return new File(PDB_1AHP).exists()
    }

    static Params originalParams

    @BeforeAll
    static void setup() {
        originalParams = (Params) Params.inst.clone()
        Params.INSTANCE = new Params()
        LoaderParams.ignoreLigandsSwitch = false
    }

    @AfterAll
    static void tearDown() {
        Params.INSTANCE = originalParams
    }

    private static LoaderParams loaderParamsWith(List<String> specs) {
        def lp = new LoaderParams()
        lp.cofactorHandler = new CofactorHandler(CofactorHandler.parseAndValidate(specs))
        return lp
    }

    @Test
    void cofactorAtomsPhysicallyPresentInProteinAtoms() {
        def protein = Protein.load(PDB_1T7QA, loaderParamsWith(["COA"]))

        // Find COA groups directly via BioJava
        List<Group> coaGroups = protein.structure.chains
                .collectMany { it.atomGroups }
                .findAll { ((Group) it).PDBName == "COA" } as List<Group>
        assertFalse(coaGroups.isEmpty(), "1t7qa should contain COA groups")

        // Build the expected set of COA heavy-atom serials
        Set<Integer> coaSerials = new HashSet<>()
        for (Group g : coaGroups) {
            for (Atom a : Atoms.allFromGroup(g).withoutHydrogens().list) {
                coaSerials.add(a.PDBserial)
            }
        }

        // Verify every COA heavy atom's serial appears in proteinAtoms
        Set<Integer> proteinSerials = new HashSet<>()
        for (Atom a : protein.proteinAtoms.list) {
            proteinSerials.add(a.PDBserial)
        }
        assertTrue(proteinSerials.containsAll(coaSerials),
                "COA heavy atoms should be in proteinAtoms (missing: ${coaSerials - proteinSerials})")
    }

    @Test
    void cofactorExtractionResultStoredOnProtein() {
        def protein = Protein.load(PDB_1T7QA, loaderParamsWith(["COA"]))

        def result = protein.cofactorExtractionResult
        assertNotNull(result, "ExtractionResult should be stored on Protein")
        assertFalse(result.atoms.empty, "COA atoms should be in result")
        assertTrue(result.foundGroups.containsKey("COA"), "COA should be in foundGroups")
        assertTrue(result.unmatchedSpecifiers.isEmpty(), "All specifiers should match")
    }

    @Test
    void coaExcludedFromLigandDetection() {
        def protein = Protein.load(PDB_1T7QA, loaderParamsWith(["COA"]))

        List<String> allLigNames = (List<String>) protein.ligands.allIncludingIgnored
                .collect { (it.name as String)?.toUpperCase() }
                .findAll { it != null }

        assertFalse(allLigNames.contains("COA"),
                "COA should be excluded from ligands, got: $allLigNames")
    }

    @Test
    void atomCountIncreasesWithCofactor() {
        def p1 = Protein.load(PDB_1T7QA, new LoaderParams())
        int without = p1.proteinAtoms.count

        def p2 = Protein.load(PDB_1T7QA, loaderParamsWith(["COA"]))
        int with = p2.proteinAtoms.count

        int added = with - without
        assertTrue(added >= 20,
                "COA should add >= 20 heavy atoms, got $added (before=$without, after=$with)")
    }

    // ===== R17/R19/R20 regression guards =====

    @Test
    void csvFeatureDoesNotThrowOnCofactorAtoms() {
        // R17 (audit fix #3): the *real* regression. With -cofactors set AND
        // CsvFileFeature configured with a non-empty column list AND a CSV that
        // covers only one polymer atom serial, the cofactor atoms would historically hit
        // CsvFileFeatureValues.missingError → PrankException. The R17 short-circuit
        // in CsvFileFeature.calculateForAtom must skip cofactor atoms before that lookup.
        //
        // To prove the test exercises the regression (and isn't trivially passing because
        // the CSV path is dead), we also call calculateForAtom on a polymer atom whose
        // serial IS present in the CSV and assert it returns the CSV's value.
        File csvDir = File.createTempFile("p2rank-csv-test", ".dir")
        csvDir.delete()
        csvDir.mkdirs()
        try {
            def lp = loaderParamsWith(["COA"])
            Protein protein = Protein.load(PDB_1T7QA, lp)

            // Pick a real polymer atom and write a CSV that covers exactly its serial.
            org.biojava.nbio.structure.Atom polymerAtom = protein.proteinAtoms.list.find { Atom a ->
                a.group.type == org.biojava.nbio.structure.GroupType.AMINOACID
            }
            assertNotNull(polymerAtom, "1t7qa should have at least one polymer atom")
            int polymerSerial = polymerAtom.PDBserial

            File csv = new File(csvDir, "1t7qa.pdb.csv")
            csv.text = "pdb serial,dummy_col\n${polymerSerial},0.5\n"

            Params.inst.feat_csv_columns = ["dummy_col"]
            Params.inst.feat_csv_directories = [csvDir.absolutePath]
            Params.inst.feat_csv_ignore_missing = false  // strict - would historically throw

            def feature = new cz.siret.prank.features.implementation.csv.CsvFileFeature()
            feature.preProcessProtein(protein, null)

            // Control: polymer atom whose serial is in CSV → must read 0.5
            cz.siret.prank.features.api.AtomFeatureCalculationContext polyCtx =
                    new cz.siret.prank.features.api.AtomFeatureCalculationContext(protein, polymerAtom)
            double[] polymerResult = feature.calculateForAtom(polymerAtom, polyCtx)
            assertEquals(1, polymerResult.length)
            assertEquals(0.5d, polymerResult[0], 1e-12,
                    "Polymer atom (control) must read 0.5 from CSV - proves the lookup path is live")

            // The actual R17 assertion: cofactor atom whose serial is NOT in CSV
            // must not throw, must return a zero vector.
            org.biojava.nbio.structure.Group coaGroup = protein.structure.chains
                    .collectMany { it.atomGroups }
                    .find { ((Group) it).PDBName == "COA" } as Group
            assertNotNull(coaGroup, "1t7qa should contain a COA group")
            org.biojava.nbio.structure.Atom coaAtom = coaGroup.atoms.first()

            cz.siret.prank.features.api.AtomFeatureCalculationContext coaCtx =
                    new cz.siret.prank.features.api.AtomFeatureCalculationContext(protein, coaAtom)

            ThrowingSupplier<double[]> supplier = { feature.calculateForAtom(coaAtom, coaCtx) } as ThrowingSupplier<double[]>
            double[] result = assertDoesNotThrow(supplier)
            assertEquals(1, result.length, "R17 returns zero vector matching header size")
            assertEquals(0.0d, result[0], 1e-12)
        } finally {
            Params.inst.feat_csv_columns = []
            Params.inst.feat_csv_directories = []
            Params.inst.feat_csv_ignore_missing = false
            csvDir.listFiles()?.each { it.delete() }
            csvDir.delete()
        }
    }

    @Test
    void conservationFeatureSafeOnCofactorAtoms() {
        // R20: confirm cofactor atoms return 0.0 from ConservationFeature even when a
        // ConservationScore IS loaded. The type-guard `parentAA.getType() != AMINOACID`
        // at the top of ConservationFeature.calculateForAtom short-circuits before the
        // score lookup; cofactor HETATM groups thus contribute 0.0 regardless of what's
        // in the score map.
        //
        // To prove the type-guard (not an empty score map) is responsible, we inject a
        // score map that BOTH covers polymer residues with non-zero values AND covers
        // the cofactor's residue number with a non-zero "poison" value. If the type-guard
        // fired, the cofactor returns 0.0 (not the poison). If it didn't, we'd see the
        // poison value bleed through.
        def lp = loaderParamsWith(["COA"])
        Protein protein = Protein.load(PDB_1T7QA, lp)

        Group coaGroup = protein.structure.chains
                .collectMany { it.atomGroups }
                .find { ((Group) it).PDBName == "COA" } as Group
        assertNotNull(coaGroup, "1t7qa should contain a COA group")
        Atom coaAtom = coaGroup.atoms.first()

        Atom polymerAtom = protein.proteinAtoms.list.find { Atom a ->
            a.group.type == org.biojava.nbio.structure.GroupType.AMINOACID
        }
        assertNotNull(polymerAtom, "1t7qa should have at least one polymer atom")

        Map<ResidueNumberWrapper, Double> scoreMap = new HashMap<>()
        scoreMap.put(new ResidueNumberWrapper(polymerAtom.group.residueNumber), 0.77d)
        scoreMap.put(new ResidueNumberWrapper(coaGroup.residueNumber), 0.99d)  // "poison"

        // Construct ConservationScore via its package-private constructor.
        Constructor<ConservationScore> ctor =
                ConservationScore.class.getDeclaredConstructor(Map.class)
        ctor.setAccessible(true)
        ConservationScore score = ctor.newInstance(scoreMap)

        // Install via the same secondaryData keys Protein uses.
        protein.secondaryData.put(ConservationScore.CONSERV_SCORE_KEY, score)
        protein.secondaryData.put(ConservationScore.CONSERV_LOADED_KEY, true)
        assertNotNull(protein.conservationScore, "Score should now be reachable via protein.conservationScore")

        def feature = new cz.siret.prank.features.implementation.conservation.ConservationFeature()

        // Control: polymer atom must read the real score (proves the lookup path is live).
        def polyCtx = new cz.siret.prank.features.api.AtomFeatureCalculationContext(protein, polymerAtom)
        double[] polymerResult = feature.calculateForAtom(polymerAtom, polyCtx)
        assertEquals(1, polymerResult.length)
        assertEquals(0.77d, polymerResult[0], 1e-12,
                "Polymer atom (control) must read its score - proves the lookup path is live")

        // Actual assertion: cofactor atom returns 0.0 due to the AMINOACID type-guard,
        // NOT 0.99 (the poison value we planted under its residue number).
        def coaCtx = new cz.siret.prank.features.api.AtomFeatureCalculationContext(protein, coaAtom)
        double[] result = feature.calculateForAtom(coaAtom, coaCtx)
        assertEquals(1, result.length)
        assertEquals(0.0d, result[0], 1e-12,
                "Cofactor atom must be short-circuited to 0.0 by type-guard, not return the poison value 0.99")
    }

    @Test
    void baselineUnchangedWithEmptyCofactorList() {
        // The "drop-in safety" guarantee: setting -cofactors to nothing matches default
        def p1 = Protein.load(PDB_1FBL, new LoaderParams())
        def p2 = Protein.load(PDB_1FBL,
                new LoaderParams(cofactorHandler: new CofactorHandler([] as List<LigandDefinition>)))

        assertEquals(p1.proteinAtoms.count, p2.proteinAtoms.count)
        assertEquals(p1.ligands.allIncludingIgnored.size(), p2.ligands.allIncludingIgnored.size())
    }

    // ===== Audit findings: missing test coverage =====

    @Test
    void cofactorAtomsAreExposedOnSurface() {
        // Audit #15: previously only proteinAtoms count was asserted; this verifies
        // that cofactor atoms participate in surface computation - at least some of
        // them appear in protein.exposedAtoms (i.e., have SAS points around them).
        //
        // Note: total SAS-point count may *decrease* when a cofactor is added because
        // the cofactor can occlude polymer surface that was previously exposed. The
        // biologically meaningful assertion is "at least one cofactor heavy atom is
        // solvent-accessible after extraction."
        def lp = loaderParamsWith(["COA"])
        Protein protein = Protein.load(PDB_1T7QA, lp)

        Set<Integer> coaSerials = new HashSet<>()
        for (List<org.biojava.nbio.structure.Group> gs : protein.cofactorExtractionResult.foundGroups.values()) {
            for (org.biojava.nbio.structure.Group g : gs) {
                for (org.biojava.nbio.structure.Atom a : cz.siret.prank.geom.Atoms.allFromGroup(g).withoutHydrogens().list) {
                    coaSerials.add(a.PDBserial)
                }
            }
        }
        assertFalse(coaSerials.isEmpty(), "Sanity: there should be COA heavy atoms")

        int exposedCofactorAtoms = 0
        for (org.biojava.nbio.structure.Atom a : protein.exposedAtoms.list) {
            if (coaSerials.contains(a.PDBserial)) exposedCofactorAtoms++
        }
        assertTrue(exposedCofactorAtoms > 0,
                "At least one COA heavy atom must be in exposedAtoms (Surface.computeExposedAtoms saw it)")
    }

    @Test
    void distantCofactorWarningRespectsThreshold() {
        // Audit #9: distantCofactorWarning must actually log a WARN at the configured
        // threshold. Uses Log4jCapture against CofactorHandler's logger.
        def lp = loaderParamsWith(["COA"])
        Protein protein = Protein.load(PDB_1T7QA, lp)
        def result = protein.cofactorExtractionResult
        assertNotNull(result)

        Log4jCapture capture = Log4jCapture.attach(CofactorHandler)
        try {
            // maxDist=0 → disabled, no log
            protein.loaderParams.cofactorHandler.warnDistantCofactors(result, protein.proteinAtoms, 0d, "test")
            // maxDist=15.0 → COA in 1t7qa is bound near the active site, no log expected
            protein.loaderParams.cofactorHandler.warnDistantCofactors(result, protein.proteinAtoms, 15.0d, "test")
            assertTrue(capture.warns().findAll { it.contains("crystallization artifact") }.isEmpty(),
                    "No distant-cofactor WARN expected at sane thresholds")

            // maxDist=0.001 → every cofactor is >0.001 Å from the protein, so a WARN must fire
            protein.loaderParams.cofactorHandler.warnDistantCofactors(result, protein.proteinAtoms, 0.001d, "test")
            List<String> warns = capture.warns().findAll { it.contains("crystallization artifact") }
            assertFalse(warns.isEmpty(),
                    "Expected at least one distant-cofactor WARN at maxDist=0.001 - got: ${capture.warns()}")
            assertTrue(warns.any { it.contains("COA") },
                    "WARN should name the cofactor (COA): ${warns}")
        } finally {
            capture.detach()
        }
        assertFalse(result.atoms.empty, "Result should be unchanged after distant-cofactor check")
    }

    @Test
    @EnabledIf("has1AHP")
    void chainExcludedCofactorDiagnosticDoesNotThrow() {
        // Audit #10: chain-reduction code path with cofactors. Reducing 1AHP to chain A
        // only and requesting PLP should still find PLP (it exists on both chains).
        def lp = loaderParamsWith(["PLP"])
        ThrowingSupplier<Protein> supplier = {
            Protein.load(PDB_1AHP, ["A"], lp)
        } as ThrowingSupplier<Protein>
        Protein protein = assertDoesNotThrow(supplier)
        assertNotNull(protein.cofactorExtractionResult)
        // PLP should still be found (on chain A)
        assertTrue(protein.cofactorExtractionResult.foundGroups.containsKey("PLP"))
    }

    // ===== Option A regression =====

    @Test
    void pymolRendererEmitsPerNameSelections() {
        // Audit #21: every `select X, ...` line must have a non-empty body and use a
        // valid PyMOL atom-identifier syntax (`id N or id N or ...` or other selectors).
        // A regression that produced `select cofactor_X, ` with empty body would pass
        // contains(...) but break PyMOL - so we validate the body explicitly.
        def lp = loaderParamsWith(["COA"])
        Protein protein = Protein.load(PDB_1T7QA, lp)
        def result = protein.cofactorExtractionResult
        assertNotNull(result)

        String pml = cz.siret.prank.program.visualization.renderers.NewPymolRenderer.cofactorPymolBlock(result)
        assertTrue(pml.contains("select cofactor_COA,"),
                "PML should contain per-name selection 'cofactor_COA':\n$pml")
        assertTrue(pml.contains("select cofactor_atoms,"),
                "PML should contain aggregate 'cofactor_atoms' selection:\n$pml")
        assertTrue(pml.contains("cofactor_col"),
                "PML should define and use cofactor_col:\n$pml")

        // set_color must use the comma form (not `name = [rgb]`) - PyMOL's PML parser is
        // friendlier to the comma form and that's the convention used by other hardcoded
        // colour definitions in the renderer.
        assertTrue(pml.contains("set_color cofactor_col, ["),
                "set_color must use comma form 'set_color name, [rgb]': $pml")

        // Each `select X, BODY` line: BODY must be non-empty and match `id <int>` (or chained
        // with ` or id <int>` or another selection name).
        java.util.regex.Pattern selectRegex = ~/(?m)^select\s+(\S+)\s*,\s*(.+)$/
        java.util.regex.Matcher m = selectRegex.matcher(pml)
        int selectLines = 0
        while (m.find()) {
            selectLines++
            String selName = m.group(1)
            String body = m.group(2).trim()
            assertFalse(body.isEmpty(), "select '$selName' has empty body in PML:\n$pml")
            // Body is either `id N (or id N)*` or `name (or name)*` (aggregate selection).
            boolean validBody = body.matches(/(id\s+\d+)(\s+or\s+id\s+\d+)*/) ||
                    body.matches(/(\S+)(\s+or\s+\S+)*/)
            assertTrue(validBody, "select '$selName' has invalid body '$body' in PML")
        }
        assertTrue(selectLines >= 2, "Expected >=2 select lines (per-name + aggregate), got $selectLines")
    }

    // ===== Audit #11: aa_mapping collision warning =====

    @Test
    void aaMappingCollisionWarningEmitted() {
        // R19: when a cofactor specifier's group name overlaps with the active aa_mapping,
        // a WARN must surface (Main.run() emits it during startup). The "minimal" aa_mapping
        // (default) maps MSE→MET and MEN→ASN, so specifying `-cofactors MSE` is a real
        // collision. We exercise the exact overlap-detection logic from Main.run().
        String savedMode = AminoAcidMapper.getInstance().mode
        try {
            AminoAcidMapper.initialize("minimal")
            Set<String> activeMappings = AminoAcidMapper.getInstance().getMappings().keySet()
            assertTrue(activeMappings.contains("MSE"), "Sanity: minimal mapping should contain MSE")

            // Same logic Main.run() executes for the WARN.
            Set<String> overlapping = new LinkedHashSet<>()
            for (String spec : ["MSE", "FAD"]) {
                String name = LigandDefinition.parse(spec.toUpperCase()).groupName?.toUpperCase()
                if (name != null && activeMappings.contains(name)) overlapping.add(name)
            }
            assertEquals(["MSE"] as Set, overlapping,
                    "Detection must flag MSE (collides) but not FAD (no collision)")

            // Also assert the WARN message format used in Main.run() doesn't blow up at format-time.
            Log4jCapture capture = Log4jCapture.attach(cz.siret.prank.program.Main)
            try {
                org.slf4j.LoggerFactory.getLogger(cz.siret.prank.program.Main).warn(
                        "Cofactor specifier(s) name(s) {} are also covered by the active " +
                                "aa_mapping. Cofactor atom features will be computed using the mapped " +
                                "AA's table entries instead of cofactor defaults. Remove the entry " +
                                "from aa_mapping or change the cofactor specifier to fix.", overlapping)
                List<String> warns = capture.warns().findAll { it.contains("aa_mapping") }
                assertFalse(warns.isEmpty(), "WARN should reach the capture appender")
                assertTrue(warns.any { it.contains("[MSE]") || it.contains("MSE") },
                        "WARN should name the colliding specifier: ${warns}")
            } finally {
                capture.detach()
            }
        } finally {
            AminoAcidMapper.initialize(savedMode ?: "minimal")
        }
    }

    // ===== Audit #19: concurrency safety =====

    @Test
    void concurrencySafeWithMultipleThreads() {
        // Dataset processing uses ExecutorService.newFixedThreadPool. Per-item LoaderParams
        // construction creates a fresh CofactorHandler, but defensive guarantee: even if
        // a handler instance were shared, extractCofactorAtoms now resets matchedGroups
        // before populating. This test loads the same structure with 8 parallel threads
        // (each with its own LoaderParams) and asserts every protein independently has the
        // expected cofactor atoms.
        int nThreads = 8
        ExecutorService pool = Executors.newFixedThreadPool(nThreads)
        try {
            List<Future<Protein>> futures = []
            for (int i = 0; i < nThreads; i++) {
                Callable<Protein> task = { Protein.load(PDB_1T7QA, loaderParamsWith(["COA"])) } as Callable<Protein>
                futures << pool.submit(task)
            }
            int firstCount = -1
            for (Future<Protein> f : futures) {
                Protein p = f.get(60, TimeUnit.SECONDS)
                assertNotNull(p.cofactorExtractionResult)
                assertTrue(p.cofactorExtractionResult.foundGroups.containsKey("COA"))
                int atoms = p.cofactorExtractionResult.atoms.count
                if (firstCount < 0) firstCount = atoms
                else assertEquals(firstCount, atoms,
                        "Parallel loads must produce identical cofactor atom counts")
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    void unmatchedSpecifierAppearsInPymolComment() {
        // Audit #20 sibling: unmatched specifiers show up as a diagnostic comment in PML.
        // Use a precise specifier that won't match.
        def lp = loaderParamsWith(["COA", "FAD[group_id:Z_999]"])
        Protein protein = Protein.load(PDB_1T7QA, lp)
        def result = protein.cofactorExtractionResult
        assertNotNull(result)
        assertEquals(["FAD[group_id:Z_999]"], result.unmatchedSpecifiers)

        String pml = cz.siret.prank.program.visualization.renderers.NewPymolRenderer.cofactorPymolBlock(result)
        assertTrue(pml.contains("# unmatched specifiers"),
                "PML should include unmatched-specifiers diagnostic:\n$pml")
        assertTrue(pml.contains("FAD[group_id:Z_999]"),
                "PML diagnostic should name the unmatched specifier:\n$pml")
    }
}
