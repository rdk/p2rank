package cz.siret.prank.features.implementation.table

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class AtomTableFeatureTest {

    @Test
    void testTransformValue() {
        assertEquals(8d,   AtomTableFeature.transformValue(2d, 3d, false))
        assertEquals(4d,   AtomTableFeature.transformValue(2d, 2d, false))
        assertEquals(Math.sqrt(2d), AtomTableFeature.transformValue(2d, 0.5d, false))
        assertEquals(-8d,  AtomTableFeature.transformValue(-2d, 3d, true))
        assertEquals(-4d,  AtomTableFeature.transformValue(-2d, 2d, true))
        assertEquals(-Math.sqrt(2d), AtomTableFeature.transformValue(-2d, 0.5d, true))
    }

}
