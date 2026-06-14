package cz.siret.prank.features.implementation.energy

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.loaders.LoaderParams
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.geom.Point
import cz.siret.prank.program.params.Params
import org.biojava.nbio.structure.Atom
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.api.parallel.ResourceLock

import static org.junit.jupiter.api.Assertions.*

/**
 * Regression for the Methyl-cloud SAS features' empty-cloud fallback.
 *
 * When a SAS point has no probe points within energy_cloud_radius, the feature
 * returns a zero vector. That fallback array MUST have getHeader().size()
 * elements — a hardcoded literal had drifted (19 vs 21 for the X2Full variant,
 * 1 vs 4 for the plain cloud), so the empty-cloud path produced a wrong-length
 * vector that PrankFeatureExtractor.checkCorrectLength rejects with a
 * PrankException, aborting feature extraction for the whole protein.
 */
@Isolated
@ResourceLock("Params")
class MethylEnergyCloudEmptyCloudTest {

    static final String PDB_1FBL = "distro/test_data/1fbl.pdb"

    static Params savedParams

    @BeforeAll static void setup()    { savedParams = Params.INSTANCE; Params.INSTANCE = new Params() }
    @AfterAll  static void tearDown() { Params.INSTANCE = savedParams }

    /** Feature output for a SAS point far from the protein → empty probe cloud → fallback branch. */
    private static double[] emptyCloudVector(AbstractMethylEnergyCloudSF feature) {
        Protein p = Protein.load(PDB_1FBL, new LoaderParams())
        feature.preProcessProtein(p, new ProcessedItemContext(null, [:] as Map<String, String>))
        Atom farPoint = new Point(1e6d, 1e6d, 1e6d)
        return feature.calculateForSasPoint(farPoint, new SasFeatureCalculationContext(p, null, null))
    }

    @Test
    void methylEnergyCloudFallbackMatchesHeaderLength() {
        AbstractMethylEnergyCloudSF feature = new MethylEnergyCloudSF()
        double[] vec = emptyCloudVector(feature)
        assertEquals(feature.header.size(), vec.length,
                "empty-cloud fallback must return a header-length vector")
        for (double v : vec) assertEquals(0d, v, 0d, "empty-cloud fallback must be all zeros")
    }

    @Test
    void methylEnergyCloudX2FullFallbackMatchesHeaderLength() {
        AbstractMethylEnergyCloudSF feature = new MethylEnergyCloudX2FullSF()
        double[] vec = emptyCloudVector(feature)
        assertEquals(feature.header.size(), vec.length,
                "empty-cloud fallback must return a header-length vector")
        for (double v : vec) assertEquals(0d, v, 0d, "empty-cloud fallback must be all zeros")
    }
}
