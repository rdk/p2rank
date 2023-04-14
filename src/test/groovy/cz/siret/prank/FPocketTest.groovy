package cz.siret.prank

import cz.siret.prank.domain.loaders.pockets.FPocketLoader
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

@CompileStatic
class FPocketTest {

    static final double DELTA = 0.00001

    @Test void testStatsParser() {

        FPocketLoader.FPocketStats stats = new FPocketLoader.FPocketStats()

        stats.parseLine("HEADER 0  - Pocket Score                      : -1.5909 ");
        stats.parseLine("HEADER 1  - Number of V. Vertices             :    54");
        stats.parseLine("HEADER 6  - Polarity Score                    :     4  ");
        stats.parseLine("HEADER 8  - Real volume (approximation)       : 1217.1342");

        stats.consolidate()

        assertEquals(54, stats.vornoiVertices)
        assertEquals(4, stats.polarityScore, DELTA)
        assertEquals(1217.1342d, stats.realVolumeApprox, DELTA)

        assertEquals(-1.5909d, stats.pocketScore, DELTA)

    }

}
