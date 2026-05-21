package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.program.PrankException
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class PUResNetLoaderTest {

    static final String ROOT = 'distro/test_data/predictions/PUResNet'
    static final String PROTEIN = "$ROOT/pdb/A.pdb"
    static final String POCKETS = "$ROOT/predictions/A_2024-05-21_11-58-03.995/pocket"

    /**
     * PredictionLoader contract: prediction.protein must be the queryProtein passed in.
     */
    @Test
    void predictionIsBoundToQueryProtein() {
        Protein queryProtein = Protein.load(PROTEIN)
        Prediction p = new PUResNetLoader().loadPrediction(POCKETS, queryProtein)

        assertSame(queryProtein, p.protein)
        assertTrue(p.pocketCount > 0)
    }

    /**
     * If the queryProtein has no matching PDB serials at all, the loader must
     * hard-fail rather than silently fall back to the foreign-Structure pocket
     * atoms. (Pre-fix, the silent fallback re-introduced the identity-mismatch
     * bug the loader was rewritten to eliminate.)
     */
    @Test
    void hardFailsWhenAllSerialsMissingFromQueryProtein() {
        Protein queryProtein = Protein.load(PROTEIN)
        // Replace the atom set with an empty one so every serial lookup misses.
        queryProtein.proteinAtoms = new cz.siret.prank.geom.Atoms()

        PrankException err = assertThrows(PrankException) {
            new PUResNetLoader().loadPrediction(POCKETS, queryProtein)
        }
        assertTrue(err.message.contains("re-link"),
                "exception should explain the mismatch; got: ${err.message}")
    }

    /**
     * surfaceAtoms must reference the SAME Atom instances as queryProtein
     * (identity, not equality), so downstream identity-based set operations
     * (DSO/DSWO overlap, BindingSite intersection) hit. sasPoints must be derived.
     */
    @Test
    void surfaceAtomsBelongToQueryProtein() {
        Protein queryProtein = Protein.load(PROTEIN)
        Prediction p = new PUResNetLoader().loadPrediction(POCKETS, queryProtein)

        Pocket pocket = p.pockets[0]
        assertFalse(pocket.surfaceAtoms.empty)
        Atom a = pocket.surfaceAtoms.list[0]
        // queryProtein.proteinAtoms index was built by the loader; we can rely on it here.
        assertSame(a, queryProtein.proteinAtoms.getByID(a.PDBserial))
        assertNotNull(pocket.sasPoints)
    }
}
