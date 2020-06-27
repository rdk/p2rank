package cz.siret.prank.program.api.impl

import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.program.api.PrankFacade
import cz.siret.prank.program.api.PrankPredictor
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.junit.Test

import java.nio.file.Path
import java.nio.file.Paths

import static cz.siret.prank.utils.PathUtils.path
import static org.junit.Assert.assertTrue

/**
 *
 */
@CompileStatic
class DafaultPrankPredictorTest {

    Path installDir = Paths.get("distro").toAbsolutePath()
    Path dataDir = path installDir, "test_data"
    Path outDir = path installDir, "test_output"

    Path testFile1 = path dataDir, "2W83.pdb"
    Path testFile2 = path dataDir, "1fbl.pdb.gz"

    List<Path> testFiles = [  //should be liganated proteins with easily predictable bnding sites
            testFile1,
            testFile2,
            path(dataDir, "liganated", "1a82a.pdb"),
            path(dataDir, "liganated", "1aaxa.pdb"),
            path(dataDir, "liganated", "1nlua.pdb"),
            path(dataDir, "liganated", "1t7qa.pdb"),
            path(dataDir, "liganated", "2ck3b.pdb")
    ]


    PrankPredictor predictor = PrankFacade.createPredictor(installDir);

    @Test
    public void predict() throws Exception {
//        GParsPool.withPool {
//            testFiles.eachParallel {
//                doTestPredict(it)
//            }
//        }
        testFiles.each { doTestPredict(it) }
    }

    private void doTestPredict(Path protFile) {
        Prediction prediction = predictor.predict(
                protFile
        )

        String fname = protFile.fileName.toString()
        
        assertTrue "SAS points empty! [$fname]", prediction.labeledPoints.size() > 0
        assertTrue "Predicted no pockets! [$fname]", prediction.pockets.size() > 0
        
        // Test if the first predicted pocket binds a ligand (should be true for all proteins from testFiles)

        Protein liganatedProtein = Protein.load(protFile.toString(), new LoaderParams(ignoreLigands: false))

        assertTrue "Testing on protein with no ligands! [$fname]", liganatedProtein.ligandCount > 0

        Atom pocketCenter = prediction.pockets.head().centroid
        double dca = liganatedProtein.allLigandAtoms.dist(pocketCenter)

        assertTrue "The first predicted pocket does not bind a ligand! [$fname]", dca <= 4.0

    }

    @Test
    public void runPrediction() throws Exception {

        Futils.delete(outDir.toString())

        Path testOutDir = path(outDir, "predict_2W83_test")
        predictor.runPrediction(
                testFile1,
                testOutDir )

        def outf = testOutDir.toString() + "/2W83.pdb_predictions.csv"

        assertTrue Futils.exists(outf)
        assertTrue Futils.size(outf) > 0
    }

}