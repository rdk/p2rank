package cz.siret.prank.domain

import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.geom.Atoms
import cz.siret.prank.utils.PdbUtils
import org.biojava.nbio.structure.Structure
import org.junit.jupiter.api.Test

import groovy.transform.CompileStatic

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class ChainReductionTest {

    static final String TEST_PROTEIN = "distro/test_data/2W83.pdb"

    @Test
    void reduceToValidChainProducesNonEmptyStructure() {
        Protein protein = Protein.load(TEST_PROTEIN, new LoaderParams())
        Structure reduced = PdbUtils.reduceStructureToChains(protein.structure, ["A"])

        assertTrue(reduced.chains.size() > 0, "reducing to chain A should produce at least one chain")
    }

    @Test
    void reduceToValidChainPreservesAtoms() {
        Protein protein = Protein.load(TEST_PROTEIN, new LoaderParams())
        Structure original = protein.structure
        Structure reduced = PdbUtils.reduceStructureToChains(original, ["A"])

        int originalAtomCount = Atoms.allFromStructure(original).count
        int reducedAtomCount = Atoms.allFromStructure(reduced).count

        assertTrue(reducedAtomCount > 0, "reduced structure should contain atoms")
        assertTrue(reducedAtomCount < originalAtomCount,
                "reducing multi-chain 2W83 to one chain should have fewer atoms")
    }

    @Test
    void reduceToNonExistentChainProducesEmptyStructure() {
        Protein protein = Protein.load(TEST_PROTEIN, new LoaderParams())
        Structure reduced = PdbUtils.reduceStructureToChains(protein.structure, ["Z"])

        assertEquals(0, reduced.chains.size(),
                "reducing to non-existent chain Z should produce 0 chains")
    }

    @Test
    void chainSelectionIsCaseSensitive() {
        Protein protein = Protein.load(TEST_PROTEIN, new LoaderParams())

        Structure reducedUpper = PdbUtils.reduceStructureToChains(protein.structure, ["A"])
        Structure reducedLower = PdbUtils.reduceStructureToChains(protein.structure, ["a"])

        // 2W83 has chain "A" (uppercase); lowercase "a" should not match
        assertTrue(reducedUpper.chains.size() > 0,
                "chain A (uppercase) should be found in 2W83")
        assertEquals(0, reducedLower.chains.size(),
                "chain a (lowercase) should not match chain A in 2W83")
    }

    @Test
    void loadProteinWithOnlyChainsParameter() {
        // Test the Protein.load overload that accepts onlyChains directly
        Protein proteinAll = Protein.load(TEST_PROTEIN, new LoaderParams())
        Protein proteinA = Protein.load(TEST_PROTEIN, ["A"], new LoaderParams())

        assertNotNull(proteinA.proteinAtoms)
        assertTrue(proteinA.proteinAtoms.count > 0,
                "protein loaded with chain A filter should have atoms")
        assertTrue(proteinA.proteinAtoms.count < proteinAll.proteinAtoms.count,
                "chain-filtered 2W83 (5 chains) should have fewer atoms than full")
    }

}
