package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.utils.Futils
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 *
 */
class FPocketLoaderTest {

    static String dir = 'src/test/resources/data/fpocket/'


    Prediction loadPredictionsFor(String structFile) {
        String extension = Futils.lastExt(structFile)
        String baseName = Futils.removeLastExtension(Futils.shortName(structFile))
        String predictionDir = Futils.dir(structFile) + "/" + baseName + "_out"
        String predictionFile = "$predictionDir/${baseName}_out.${extension}"

        Protein queryProtein = Protein.load(structFile)

        new FPocketLoader().loadPrediction(predictionFile, queryProtein)
    }

    static final double DELTA = 0.00001d

    @Test
    void testFpocket42Pdb() {

        Prediction pa = loadPredictionsFor("$dir/fpocket-4-2/pdb/1fbl.pdb")
        assertEquals 19, pa.pocketCount
        assertEquals 0.6703, pa.pockets[0].score, DELTA

        Prediction pb = loadPredictionsFor("$dir/fpocket-4-2/pdb/2W83.pdb")
        assertEquals 39, pb.pocketCount
        assertEquals 0.9050, pb.pockets[0].score, DELTA
    }


    @Test
    void testFpocket42Cif() {
        Prediction pa = loadPredictionsFor("$dir/fpocket-4-2/cif/1fbl.cif")
        assertEquals 19, pa.pocketCount
        assertEquals 0.6703, pa.pockets[0].score, DELTA

        Prediction pb = loadPredictionsFor("$dir/fpocket-4-2/cif/2W83.cif")
        assertEquals 39, pb.pocketCount
        assertEquals 0.9050, pb.pockets[0].score, DELTA
    }

    
}