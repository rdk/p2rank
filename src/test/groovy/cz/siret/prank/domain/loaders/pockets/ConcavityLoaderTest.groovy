package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertSame
import static org.junit.jupiter.api.Assertions.assertTrue

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

}
