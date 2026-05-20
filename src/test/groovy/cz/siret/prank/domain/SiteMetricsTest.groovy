package cz.siret.prank.domain

import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.prediction.pockets.PrankPocket
import cz.siret.prank.prediction.pockets.criteria.DCA
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.results.EvalContext
import cz.siret.prank.program.routines.results.Evaluation
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests for ResidueSite and site-based evaluation.
 */
@Slf4j
@Isolated
@ResourceLock("Params")
@CompileStatic
class SiteMetricsTest {

    static final String TEST_PROTEIN = 'distro/test_data/liganated/1a82a.pdb'

    static Protein protein

    @BeforeAll
    static void loadProtein() {
        protein = Protein.load(TEST_PROTEIN)
        protein.calcuateSurfaceAndExposedAtoms()
    }

//===========================================================================================================//
// ResidueSite unit tests
//===========================================================================================================//

    @Test
    void residueSiteBasicProperties() {
        List<Residue> residues = protein.residues.toList().subList(0, 5)

        ResidueSite site = makeSite("test_site", residues)

        assertEquals("test_site", site.label)
        assertEquals("test_site", site.name)
        assertEquals(5, site.residues.size())
        assertEquals("site test_site residues:5", site.toString())
    }

    @Test
    void residueSiteAtomsAreUnionOfResidueAtoms() {
        List<Residue> residues = protein.residues.toList().subList(0, 3)

        ResidueSite site = makeSite("site1", residues)
        Atoms siteAtoms = site.atoms

        // atoms should be the union of all residue atoms
        int expectedCount = Atoms.union(residues.collect { it.atoms }).count
        assertEquals(expectedCount, siteAtoms.count)

        // each residue's atoms must be present in site atoms
        Set siteAtomSet = siteAtoms.toSet()
        for (Residue r : residues) {
            for (def a : r.atoms) {
                assertTrue(siteAtomSet.contains(a), "Site atoms should contain all atoms from residue $r")
            }
        }
    }

    @Test
    void residueSiteAtomsCached() {
        List<Residue> residues = protein.residues.toList().subList(0, 3)
        ResidueSite site = makeSite("site1", residues)

        Atoms first = site.atoms
        Atoms second = site.atoms
        assertSame(first, second, "getAtoms() should return cached instance")
    }

    @Test
    void residueSiteCentroidFromConstructor() {
        List<Residue> residues = protein.residues.toList().subList(0, 5)
        Atoms resAtoms = Atoms.union(residues.collect { it.atoms })
        org.biojava.nbio.structure.Atom expectedCentroid = resAtoms.centerOfMass

        ResidueSite site = new ResidueSite("site1", expectedCentroid, residues, protein)

        // centroid should be the one passed to the constructor
        assertSame(expectedCentroid, site.explicitCenter)
    }

    @Test
    void residueSiteCalcCentroidFromResidues() {
        List<Residue> residues = protein.exposedResidues.toList().subList(0, 10)
        ResidueSite site = makeSite("site1", residues)

        def calcCentroid = site.calcCentroidFromResidues()
        assertNotNull(calcCentroid)

        // calcCentroidFromResidues returns centerOfMass of SAS points
        def expectedCentroid = site.sasPoints.centerOfMass
        assertEquals(expectedCentroid.x, calcCentroid.x, 0.001)
        assertEquals(expectedCentroid.y, calcCentroid.y, 0.001)
        assertEquals(expectedCentroid.z, calcCentroid.z, 0.001)
    }

    @Test
    void residueSiteSasPoints() {
        // getSasCutoffDist() = solvent_radius + surface_additional_cutoff = 3.4 A
        // This is large enough to find SAS points near exposed residue atoms
        List<Residue> residues = protein.exposedResidues.toList().subList(0, 10)
        ResidueSite site = makeSite("site1", residues)

        Atoms sasPoints = site.sasPoints
        assertNotNull(sasPoints)
        assertTrue(sasPoints.count > 0,
                "Exposed residues should have nearby SAS points with sasCutoffDist")
    }

    @Test
    void residueSiteRejectsEmptyResidues() {
        assertThrows(AssertionError) {
            new ResidueSite("empty", null, [], protein)
        }
    }

    // TODO: test ResidueSite with residues from multiple chains
    // TODO: test SAS point caching and clearing (site.@sasPoints = null)
    // TODO: test ResidueSite with single residue (edge case)

//===========================================================================================================//
// Site-based evaluation integration tests
//===========================================================================================================//

    @Test
    void siteEvaluationProducesLigandAndPocketRows() {
        // Build a ResidueSite from a few residues near a known position
        List<Residue> residues = protein.residues.toList().subList(20, 30)
        ResidueSite site = makeSite("BS1", residues)

        // Create a mock pocket near the site
        Pocket pocket = makePocket("pocket1", site.centroid, site.atoms, 1)

        // Build a PredictionPair with sites populated
        Protein predProtein = Protein.load(TEST_PROTEIN)
        predProtein.calcuateSurfaceAndExposedAtoms()
        predProtein.sites.add(site)

        Prediction prediction = new Prediction(predProtein, [pocket])
        PredictionPair pair = new PredictionPair("test", predProtein, null, prediction)

        // Run evaluation
        Evaluation eval = new Evaluation()
        eval.addPrediction(pair, [pocket])

        // Verify it took the sites path (not the ligand path)
        assertEquals(1, eval.proteinRows.size())
        assertEquals(1, eval.ligandRows.size())
        assertEquals(1, eval.pocketRows.size())

        // Verify LigRow is populated with site info
        Evaluation.LigRow ligRow = eval.ligandRows[0]
        assertEquals("test", ligRow.protName)
        assertEquals("BS1", ligRow.ligName)
        assertEquals("", ligRow.ligCode) // sites have no ligCode
        assertEquals(1, ligRow.ligCount)
        assertTrue(ligRow.atoms > 0)

        // N/A fields should be NaN or 0 for sites
        assertEquals(0, ligRow.contactAtoms)
        assertTrue(Double.isNaN(ligRow.centerToProtDist))
        assertTrue(Double.isNaN(ligRow.proteinDist))
        assertTrue(Double.isNaN(ligRow.sasDist))
        assertTrue(Double.isNaN(ligRow.avgPointScore))

        // Verify counters
        assertEquals(1, eval.ligandCount)
        assertEquals(1, eval.proteinCount)
    }

    @Test
    void siteEvaluationUsesLigandPathWhenSitesPopulatedFromLigands() {
        // When sites are populated from ligands, addPrediction should use ligand mode
        Protein predProtein = Protein.load(TEST_PROTEIN)
        predProtein.calcuateSurfaceAndExposedAtoms()
        predProtein.populateSitesFromLigands()

        Pocket pocket = makePocket("pocket1", predProtein.proteinAtoms.centerOfMass, predProtein.proteinAtoms, 1)
        Prediction prediction = new Prediction(predProtein, [pocket])
        PredictionPair pair = new PredictionPair("test", predProtein, null, prediction)

        Evaluation eval = new Evaluation()
        eval.addPrediction(pair, [pocket])

        // Should have used the ligand path — ligandRows come from actual ligands
        assertEquals(1, eval.proteinRows.size())
        int ligandCount = predProtein.ligands.relevantLigandCount
        assertEquals(ligandCount, eval.ligandRows.size())
    }

    @Test
    void siteEvaluationIdentifiesPocketForSite() {
        // Place pocket centroid close to site atoms so DCA identifies it
        List<Residue> residues = protein.residues.toList().subList(20, 30)
        ResidueSite site = makeSite("BS1", residues)

        // Pocket centroid = site centroid => DCA distance = 0 => identified
        Pocket pocket = makePocket("pocket1", site.centroid, site.atoms, 1)

        DCA dca4 = new DCA("DCA_4", 4.0d)
        EvalContext ctx = new EvalContext()

        assertTrue(dca4.isIdentified(site, pocket, ctx),
                "DCA should identify pocket when centroid is at site center")
    }

    @Test
    void dcaCriterionWorksWithBothLigandAndResidueSite() {
        // Verify DCA works polymorphically with both BindingSite implementations
        List<Residue> residues = protein.residues.toList().subList(20, 30)
        ResidueSite site = makeSite("BS1", residues)

        Pocket pocket = makePocket("pocket1", site.centroid, site.atoms, 1)
        DCA dca4 = new DCA("DCA_4", 4.0d)
        EvalContext ctx = new EvalContext()

        // Test with ResidueSite
        boolean siteResult = dca4.isIdentified(site, pocket, ctx)
        double siteScore = dca4.score(site, pocket)

        // Test with Ligand using same atoms
        Ligand ligand = new Ligand(site.atoms, protein)
        boolean ligandResult = dca4.isIdentified(ligand, pocket, ctx)
        double ligandScore = dca4.score(ligand, pocket)

        // Both should give the same result for same atoms and pocket
        assertEquals(siteResult, ligandResult)
        assertEquals(siteScore, ligandScore, 0.001)
    }

    // TODO: test addSitesPrediction with multiple sites
    // TODO: test that PocketRow.ligName is populated via findSiteForPocket
    // TODO: test site-based evaluation success rate calculation (calcSuccessRate)
    // TODO: test other criteria (DCC, DSO, DSWO, DPA, DSA) with ResidueSite
    // TODO: test evaluation getStats() with site-based results
    // TODO: test with sites that are far from any pocket (unidentified case)

//===========================================================================================================//
// ca_atoms_centroid tests
//===========================================================================================================//

    @Test
    void caCentroidMethodWorksForResidueSite() {
        List<Residue> residues = protein.residues.toList().subList(0, 10)
        ResidueSite site = makeSite("ca_test", residues)

        String savedMethod = Params.inst.site_eval_center_method
        try {
            Params.inst.site_eval_center_method = "ca_atoms_centroid"

            def result = site.getCenterForEval()
            def expected = Struct.calcCaCentroid(residues)

            assertNotNull(result)
            assertEquals(expected.x, result.x, 0.001)
            assertEquals(expected.y, result.y, 0.001)
            assertEquals(expected.z, result.z, 0.001)
        } finally {
            Params.inst.site_eval_center_method = savedMethod
        }
    }

    @Test
    void caCentroidMethodWorksForLigand() {
        Ligand ligand = protein.ligands.relevantLigands[0]
        assertNotNull(ligand, "Test protein should have at least one relevant ligand")

        String savedMethod = Params.inst.site_eval_center_method
        try {
            Params.inst.site_eval_center_method = "ca_atoms_centroid"

            def result = ligand.getCenterForEval()
            assertNotNull(result, "ca_atoms_centroid should return non-null for a ligand with nearby protein residues")

            // Result should be within reasonable distance of the ligand
            double dist = protein.proteinAtoms.dist(result)
            assertTrue(dist < 20.0, "CA centroid should be near the protein surface")
        } finally {
            Params.inst.site_eval_center_method = savedMethod
        }
    }

//===========================================================================================================//
// contact_atoms_centroid tests
//===========================================================================================================//

    @Test
    void contactAtomsCentroidMethodWorksForLigand() {
        Ligand ligand = protein.ligands.relevantLigands[0]
        assertNotNull(ligand, "Test protein should have at least one relevant ligand")

        String savedMethod = Params.inst.site_eval_center_method
        try {
            Params.inst.site_eval_center_method = "contact_atoms_centroid"

            def result = ligand.getCenterForEval()
            assertNotNull(result, "contact_atoms_centroid should return non-null for a ligand with nearby protein atoms")

            // Result should be within reasonable distance of the ligand
            double dist = protein.proteinAtoms.dist(result)
            assertTrue(dist < 20.0, "Contact atoms centroid should be near the protein surface")
        } finally {
            Params.inst.site_eval_center_method = savedMethod
        }
    }

    @Test
    void contactAtomsCentroidNotSupportedForResidueSite() {
        List<Residue> residues = protein.residues.toList().subList(0, 5)
        ResidueSite site = makeSite("test", residues)

        String savedMethod = Params.inst.site_eval_center_method
        try {
            Params.inst.site_eval_center_method = "contact_atoms_centroid"

            assertThrows(IllegalArgumentException) {
                site.getCenterForEval()
            }
        } finally {
            Params.inst.site_eval_center_method = savedMethod
        }
    }

//===========================================================================================================//
// Helpers
//===========================================================================================================//

    /**
     * Creates a ResidueSite with centroid derived from the residue atoms.
     */
    private static ResidueSite makeSite(String name, List<Residue> residues) {
        Atoms atoms = Atoms.union(residues.collect { it.atoms })
        return new ResidueSite(name, atoms.centerOfMass, residues, protein)
    }

    /**
     * Creates a simple Pocket at the given centroid position.
     */
    private static Pocket makePocket(String name, org.biojava.nbio.structure.Atom centroid, Atoms surfaceAtoms, int rank) {
        Pocket pocket = new PrankPocket(centroid, 1.0d, new Atoms(), [])
        pocket.name = name
        pocket.surfaceAtoms = surfaceAtoms
        pocket.rank = rank
        pocket.newRank = rank
        return pocket
    }

}
