package cz.siret.prank.domain.loaders.pockets

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Minimal smoke tests for pocket loader classes that previously lacked test coverage.
 * Verifies that each class can be instantiated and conforms to the PredictionLoader hierarchy.
 */
@CompileStatic
class MissingPocketLoadersTest {

    @Test
    void deepSiteLoaderInstantiates() {
        def loader = new DeepSiteLoader()
        assertNotNull(loader)
        assertTrue(loader instanceof PredictionLoader)
    }

    @Test
    void liseLoaderInstantiates() {
        def loader = new LiseLoader()
        assertNotNull(loader)
        assertTrue(loader instanceof PredictionLoader)
    }

    @Test
    void metaPocket2LoaderInstantiates() {
        def loader = new MetaPocket2Loader()
        assertNotNull(loader)
        assertTrue(loader instanceof PredictionLoader)
    }

    @Test
    void siteHoundLoaderInstantiates() {
        def loader = new SiteHoundLoader()
        assertNotNull(loader)
        assertTrue(loader instanceof PredictionLoader)
    }

    @Test
    void p2RankLoaderInstantiates() {
        def loader = new P2RankLoader()
        assertNotNull(loader)
        assertTrue(loader instanceof PredictionLoader)
    }

    @Test
    void allLoadersShareCommonBaseClass() {
        List<PredictionLoader> loaders = [
            new DeepSiteLoader(),
            new LiseLoader(),
            new MetaPocket2Loader(),
            new SiteHoundLoader(),
            new P2RankLoader()
        ]

        for (PredictionLoader loader : loaders) {
            assertNotNull(loader, "loader instance should not be null")
            assertTrue(loader instanceof PredictionLoader,
                    "${loader.class.simpleName} should extend PredictionLoader")
        }
    }

}
