package cz.siret.prank.program.api.impl

import cz.siret.prank.domain.Prediction
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.program.api.PrankFacade
import cz.siret.prank.program.api.PrankPredictor
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import java.nio.file.Path
import java.nio.file.Paths

import static cz.siret.prank.utils.PathUtils.path
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 *
 */
@Isolated
@ResourceLock("Params")
@CompileStatic
class DafaultPrankPredictorTest {

    Path installDir = Paths.get("distro").toAbsolutePath()
    Path dataDir = path installDir, "test_data"
    Path outDir = path installDir, "test_output"

    Path pdb_1fbl = path dataDir, "1fbl.pdb.gz"
    Path cif_1fbl = path dataDir, "1fbl.cif"
    Path bcif_1fbl = path dataDir, "1fbl.bcif"

    Path pdb_2W83 = path dataDir, "2W83.pdb"
    Path cif_2W83 = path dataDir, "2W83.cif.zst"
    Path bcif_2W83 = path dataDir, "2W83.bcif.gz"

    List<Path> testFiles = [  //should be liganated proteins with easily predictable binding sites
            pdb_1fbl,
            cif_1fbl,
            bcif_1fbl,
            pdb_2W83,
            cif_2W83,
            bcif_2W83,
            path(dataDir, "liganated", "1a82a.pdb"),
            path(dataDir, "liganated", "1aaxa.pdb"),
            path(dataDir, "liganated", "1nlu.pdb"),
            path(dataDir, "liganated", "1t7qa.pdb"),
            path(dataDir, "liganated", "2ck3b.pdb")
    ]


    PrankPredictor predictor = PrankFacade.createPredictor(installDir);


    @BeforeAll
    static void initAll() {
        Params.INSTANCE = new Params()
    }

    @AfterAll
    static void tearDownAll() {
        Params.INSTANCE = new Params()
    }


    
    @Test
    void predict() throws Exception {
        testFiles.each { doTestPredict(it) }
    }

    private void doTestPredict(Path protFile) {
        Prediction prediction = predictor.predict(protFile)

        String fname = protFile.fileName.toString()

        assertNotNull prediction.pockets, "pockets list is null! [$fname]"
        assertTrue prediction.pockets.size() > 0, "Predicted no pockets! [$fname]"
        assertNotNull prediction.labeledPoints, "labeledPoints list is null! [$fname]"
        assertTrue prediction.labeledPoints.size() > 0, "SAS points empty! [$fname]"

        // Test if the first predicted pocket binds a ligand (should be true for all proteins from testFiles)

        Protein liganatedProtein = Protein.load(protFile.toString(), new LoaderParams(ignoreLigands: false))

        assertTrue liganatedProtein.ligandCount > 0, "Testing on protein with no ligands! [$fname]"

        Atom pocketCenter = prediction.pockets.head().centroid
        double dca = liganatedProtein.allRelevantLigandAtoms.dist(pocketCenter)

        assertTrue dca <= 4.0, "The first predicted pocket does not bind a ligand! [$fname]"

    }

    @Test
    void runPrediction() throws Exception {

        Futils.delete(outDir.toString())

        Path testOutDir = path(outDir, "predict_2W83_test")
        predictor.runPrediction(pdb_2W83, testOutDir)

        def outf = testOutDir.toString() + "/2W83.pdb_predictions.csv"

        assertTrue Futils.exists(outf)
        assertTrue Futils.size(outf) > 0
    }

}