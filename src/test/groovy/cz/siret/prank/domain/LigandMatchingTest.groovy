package cz.siret.prank.domain

import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.GroupType
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests for ligand detection, specifically for ligands that BioJava misclassifies
 * based on its Chemical Component Dictionary.
 *
 * BioJava assigns GroupType based on chemical classification, not structural role:
 * - GDP, GTP, ATP -> GroupType.NUCLEOTIDE (nucleotide derivatives)
 * - SHR and similar -> GroupType.AMINOACID (amino acid derivatives)
 * - Most other ligands -> GroupType.HETATM
 *
 * All these should be detected as ligand candidates when in non-polymer chains.
 *
 * @see Struct#isLigandCandidateGroup(Group)
 */
@Slf4j
@Isolated
@ResourceLock("Params")
@CompileStatic
class LigandMatchingTest {

    static final String DIR = 'src/test/resources/data/tricky_cases/ligand_detection'

    static Params savedParams

    @BeforeAll
    static void setUp() {
        savedParams = Params.INSTANCE
        Params.INSTANCE = new Params()
    }

    @AfterAll
    static void tearDown() {
        Params.INSTANCE = savedParams
    }

    // ================ Nucleotide ligand tests ================

    /**
     * GTP in 2W83.pdb - detected as ligand via the traditional HETATM path.
     */
    @Test
    void testNucleotideLigandDetection() {
        Protein protein = Protein.load('distro/test_data/2W83.pdb')

        List<Group> ligandGroups = Struct.getLigandGroups(protein)
        List<String> ligandNames = ligandGroups*.PDBName

        assertTrue(ligandNames.contains('GTP'),
            "GTP should be detected as a ligand")
    }

    /**
     * GDP in 1a2kC.pdb - BioJava classifies it as GroupType.NUCLEOTIDE
     * in a NONPOLYMER chain. Must be detected via the non-polymer chain path.
     */
    @Test
    void testNucleotideLigandInNonPolymerChain() {
        Protein protein = Protein.load("$DIR/1a2kC.pdb")

        List<Group> ligandGroups = Struct.getLigandGroups(protein)

        assertTrue(ligandGroups.any { it.PDBName == 'GDP' },
            "GDP (GroupType.NUCLEOTIDE in NONPOLYMER chain) should be detected as a ligand")
    }

    // ================ Amino acid derivative ligand tests ================

    /**
     * SHR in 1e5qA.pdb - BioJava classifies it as GroupType.AMINOACID
     * in a NONPOLYMER chain. Must be detected via the non-polymer chain path.
     */
    @Test
    void testAminoAcidLigandInNonPolymerChain() {
        Protein protein = Protein.load("$DIR/1e5qA.pdb")

        List<Group> ligandGroups = Struct.getLigandGroups(protein)
        List<String> ligandNames = ligandGroups*.PDBName

        log.info "Ligand groups in 1e5qA: ${ligandNames}"

        assertTrue(ligandNames.contains('SHR'),
            "SHR (GroupType.AMINOACID in NONPOLYMER chain) should be detected as a ligand")
        assertTrue(ligandNames.contains('NDP'),
            "NDP (GroupType.HETATM) should also be detected")
    }

    // ================ isLigandCandidateGroup unit tests ================

    /**
     * HETATM groups should pass both isHetGroup() and isLigandCandidateGroup().
     */
    @Test
    void testIsLigandCandidateGroupForHetatm() {
        Protein protein = Protein.load('distro/test_data/2W83.pdb')
        List<Group> allGroups = Struct.getGroups(protein.structure)

        List<Group> hetGroups = allGroups.findAll { Struct.isHetGroup(it) }
        assertFalse(hetGroups.isEmpty(), "2W83 should contain HETATM groups")

        for (Group g : hetGroups) {
            assertTrue(Struct.isLigandCandidateGroup(g),
                "isLigandCandidateGroup should return true for HETATM group ${g.PDBName}")
        }
    }

    /**
     * Non-HETATM groups in NONPOLYMER chains should pass isLigandCandidateGroup()
     * but NOT isHetGroup() (backward compatibility).
     */
    @Test
    void testIsLigandCandidateGroupForNonPolymerChain() {
        // 1a2kC.pdb: GDP is NUCLEOTIDE in NONPOLYMER chain
        Protein protein1 = Protein.load("$DIR/1a2kC.pdb")
        Group gdp = Struct.getGroups(protein1.structure).find { it.PDBName == 'GDP' }

        assertNotNull(gdp, "GDP group should exist in structure")
        assertEquals(GroupType.NUCLEOTIDE, gdp.type, "GDP should have GroupType.NUCLEOTIDE")
        assertFalse(Struct.isHetGroup(gdp), "isHetGroup should return false for NUCLEOTIDE group")
        assertTrue(Struct.isLigandCandidateGroup(gdp), "isLigandCandidateGroup should return true for GDP in NONPOLYMER chain")

        // 1e5qA.pdb: SHR is AMINOACID in NONPOLYMER chain
        Protein protein2 = Protein.load("$DIR/1e5qA.pdb")
        Group shr = Struct.getGroups(protein2.structure).find { it.PDBName == 'SHR' }

        assertNotNull(shr, "SHR group should exist in structure")
        assertEquals(GroupType.AMINOACID, shr.type, "SHR should have GroupType.AMINOACID")
        assertFalse(Struct.isHetGroup(shr), "isHetGroup should return false for AMINOACID group")
        assertTrue(Struct.isLigandCandidateGroup(shr), "isLigandCandidateGroup should return true for SHR in NONPOLYMER chain")
    }

    // ================ End-to-end tests ================

    /**
     * Full protein load with explicit ligand definition for GDP.
     * Reproduces the original error: "Ligand definition 'GDP' in protein '1a2kC.pdb' matches no ligands."
     */
    @Test
    void testFullLoadWithExplicitLigandDefinition() {
        LoaderParams lp = new LoaderParams()
        lp.relevantLigandsDefined = true
        lp.relevantLigandDefinitions = [Dataset.LigandDefinition.parse("GDP")]

        // This should NOT throw PrankException
        Protein protein = Protein.load("$DIR/1a2kC.pdb", lp)

        assertNotNull(protein.ligands)
        assertFalse(protein.ligands.relevantLigands.isEmpty(),
            "GDP should be loaded as a relevant ligand")
    }
}
