package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Pocket
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class ConcavityLoaderTest {

    static String dir = 'src/test/resources/data/concavity/pocketfinder/1a26A'

    /**
     * The PredictionLoader contract: prediction.protein must be the queryProtein
     * passed in (the protein from .ds column 1), not anything the loader
     * synthesizes internally. Conservation lookup and several feature extractors
     * key on prediction.protein.fileName, so violating this contract silently
     * breaks downstream features. ConcavityLoader regressed on this previously.
     */
    @Test
    void predictionIsBoundToQueryProtein() {
        Protein queryProtein = Protein.load("$dir/1a26A.pdb")
        Prediction p = new ConcavityLoader().loadPrediction(
                "$dir/1a26A_pocketfinder_pocket.pdb", queryProtein)

        assertSame(queryProtein, p.protein)
        assertTrue(p.pocketCount > 0)
    }

    /**
     * Loader hygiene: surfaceAtoms must be the SAME Atom instances as queryProtein's
     * exposed atoms (identity, not just equality), so downstream set operations
     * (DSO/DSWO overlap, BindingSite intersection) hit. sasPoints must also be
     * derived so the pocket isn't a half-built object.
     */
    @Test
    void surfaceAtomsBelongToQueryProtein() {
        Protein queryProtein = Protein.load("$dir/1a26A.pdb")
        Prediction p = new ConcavityLoader().loadPrediction(
                "$dir/1a26A_pocketfinder_pocket.pdb", queryProtein)

        Pocket pocket = p.pockets[0]
        assertFalse(pocket.surfaceAtoms.empty)
        Atom a = pocket.surfaceAtoms.list[0]
        // Index is not auto-built by the loader; build before identity lookup.
        assertSame(a, queryProtein.exposedAtoms.withIndex().getByID(a.PDBserial))
        assertNotNull(pocket.sasPoints)
    }

}
