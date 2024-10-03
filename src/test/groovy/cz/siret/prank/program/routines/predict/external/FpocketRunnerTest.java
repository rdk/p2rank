package cz.siret.prank.program.routines.predict.external;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class FpocketRunnerTest {

    @Test
    void testFpocketOutDir() {
        assertEquals("a1234_out", FpocketRunner.fpocketOutDir("a1234.pdb"));
        assertEquals("a1234.bb_out", FpocketRunner.fpocketOutDir("a1234.bb.pdb"));
        assertEquals("a.001.001.001_1s69a_out", FpocketRunner.fpocketOutDir("a.001.001.001_1s69a.pdb"));
    }

}
