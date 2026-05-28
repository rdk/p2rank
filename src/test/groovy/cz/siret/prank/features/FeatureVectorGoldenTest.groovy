package cz.siret.prank.features

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.program.params.Params
import cz.siret.prank.program.routines.predict.ExportPointsRoutine
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static org.junit.jupiter.api.Assertions.*

/**
 * Golden test for the feature vector pipeline.
 * Verifies the feature header (column names and order) and that feature
 * values are finite and within expected ranges.
 *
 * Catches regressions in: ChemFeature, ProtrusionFeature, BfactorFeature,
 * AtomTableFeature, VolsiteFeature, PrankFeatureExtractor, FeatureSetup,
 * WeightFun, and column ordering.
 */
@Isolated
@ResourceLock("Params")
@CompileStatic
class FeatureVectorGoldenTest {

    static final String PDB_1FBL = "distro/test_data/1fbl.pdb"
    static final String OUT_DIR = "distro/test_output/feature_vector_golden"

    static Params originalParams
    static boolean origIgnoreLigandsSwitch

    @BeforeAll
    static void setup() {
        originalParams = (Params) Params.inst.clone()
        origIgnoreLigandsSwitch = LoaderParams.ignoreLigandsSwitch
        Params.INSTANCE = new Params()
    }

    @AfterAll
    static void tearDown() {
        Params.INSTANCE = originalParams
        LoaderParams.ignoreLigandsSwitch = origIgnoreLigandsSwitch
        try { Futils.delete(OUT_DIR) } catch (Exception ignored) {}
    }

    @Test
    void featureHeaderMatchesExpectedForDefaultConfig() {
        Params.inst.export_points_format = "csv"
        // Use the shipped default config's feature set
        Params.inst.features = ["chem", "volsite", "protrusion", "bfactor"]

        Dataset dataset = Dataset.createSingleFileDataset(PDB_1FBL)
        new ExportPointsRoutine(dataset, OUT_DIR).execute()

        String csvFile = "$OUT_DIR/1fbl.pdb_points.csv"
        assertTrue(Futils.exists(csvFile), "CSV must exist")

        String header = new File(csvFile).readLines().first()
        String[] cols = header.split(",")

        // first 3 columns are coordinates
        assertEquals("x", cols[0])
        assertEquals("y", cols[1])
        assertEquals("z", cols[2])

        // chem features (24 sub-features) must come first after xyz
        List<String> expectedChemFeatures = [
            "chem.hydrophobic", "chem.hydrophilic", "chem.hydrophatyIndex",
            "chem.aliphatic", "chem.aromatic", "chem.sulfur", "chem.hydroxyl",
            "chem.basic", "chem.acidic", "chem.amide",
            "chem.posCharge", "chem.negCharge",
            "chem.hBondDonor", "chem.hBondAcceptor", "chem.hBondDonorAcceptor",
            "chem.polar", "chem.ionizable",
            "chem.atoms", "chem.atomDensity",
            "chem.atomC", "chem.atomO", "chem.atomN",
            "chem.hDonorAtoms", "chem.hAcceptorAtoms"
        ]
        for (int i = 0; i < expectedChemFeatures.size(); i++) {
            assertEquals(expectedChemFeatures[i], cols[3 + i],
                    "chem feature at index ${3+i} mismatch")
        }

        // volsite features (6 sub-features) follow chem
        int volsiteStart = 3 + expectedChemFeatures.size()
        List<String> expectedVolsite = [
            "volsite.vsAromatic", "volsite.vsCation", "volsite.vsAnion",
            "volsite.vsHydrophobic", "volsite.vsAcceptor", "volsite.vsDonor"
        ]
        for (int i = 0; i < expectedVolsite.size(); i++) {
            assertEquals(expectedVolsite[i], cols[volsiteStart + i],
                    "volsite feature at index ${volsiteStart+i} mismatch")
        }

        // protrusion follows volsite
        int protrusionIdx = volsiteStart + expectedVolsite.size()
        assertEquals("protrusion.protrusion", cols[protrusionIdx])

        // bfactor follows protrusion
        assertEquals("bfactor.bfactor", cols[protrusionIdx + 1])

        // atom_table features follow (implicitly added when atom_table_features is non-empty)
        int atomTableStart = protrusionIdx + 2
        List<String> expectedAtomTable = [
            "atom_table.apRawValids", "atom_table.apRawInvalids", "atom_table.atomicHydrophobicity"
        ]
        for (int i = 0; i < expectedAtomTable.size(); i++) {
            assertEquals(expectedAtomTable[i], cols[atomTableStart + i],
                    "atom_table feature at index ${atomTableStart+i} mismatch")
        }

        // total column count: xyz(3) + chem(24) + volsite(6) + protrusion(1) + bfactor(1) + atom_table(3) = 38
        assertEquals(38, cols.length, "total feature column count")
    }

    @Test
    void featureValuesAreFiniteAndReasonable() {
        Params.inst.export_points_format = "csv"
        Params.inst.features = ["chem", "volsite", "protrusion", "bfactor"]

        Dataset dataset = Dataset.createSingleFileDataset(PDB_1FBL)
        String outdir = OUT_DIR + "/values"
        new ExportPointsRoutine(dataset, outdir).execute()

        String csvFile = "$outdir/1fbl.pdb_points.csv"
        List<String> lines = new File(csvFile).readLines()

        String[] headerCols = lines[0].split(",")
        int numCols = headerCols.length

        int chemHydrophobicIdx = Arrays.asList(headerCols).indexOf("chem.hydrophobic")
        int protrusionIdx = Arrays.asList(headerCols).indexOf("protrusion.protrusion")
        int bfactorIdx = Arrays.asList(headerCols).indexOf("bfactor.bfactor")

        assertTrue(chemHydrophobicIdx > 0, "chem.hydrophobic must be in header")
        assertTrue(protrusionIdx > 0, "protrusion must be in header")
        assertTrue(bfactorIdx > 0, "bfactor must be in header")

        // check first 100 data rows
        int rowsToCheck = Math.min(100, lines.size() - 1)
        for (int r = 1; r <= rowsToCheck; r++) {
            String[] vals = lines[r].split(",")
            assertEquals(numCols, vals.length, "row $r must have $numCols columns")

            for (int c = 0; c < numCols; c++) {
                double v = Double.parseDouble(vals[c])
                assertTrue(Double.isFinite(v), "row $r col ${headerCols[c]}: value must be finite, got $v")
            }

            // protrusion is typically 5-30 for protein surface points
            double protrusion = Double.parseDouble(vals[protrusionIdx])
            assertTrue(protrusion >= 0, "protrusion must be >= 0, got $protrusion at row $r")
            assertTrue(protrusion < 500, "protrusion must be < 500, got $protrusion at row $r")
        }

        // guard against all-zeros: at least some feature columns must have non-zero values
        // (checks row 1 which should have real feature values for a protein surface point)
        String[] firstRow = lines[1].split(",")
        boolean hasNonZero = false
        for (int c = 3; c < numCols; c++) {
            if (Double.parseDouble(firstRow[c]) != 0.0d) {
                hasNonZero = true
                break
            }
        }
        assertTrue(hasNonZero, "feature values must not be all zeros")
    }
}
