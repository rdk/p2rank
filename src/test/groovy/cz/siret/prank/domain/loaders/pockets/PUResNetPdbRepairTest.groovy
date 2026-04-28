package cz.siret.prank.domain.loaders.pockets

import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Structure
import org.junit.jupiter.api.Test

import static cz.siret.prank.domain.loaders.pockets.PUResNetPdbRepair.repairShiftedInsertionCodes
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertSame

@CompileStatic
class PUResNetPdbRepairTest {

    /**
     * Malformed PUResNet input with the icode in column 26 instead of 27,
     * for both 2-digit and 3-digit residue numbers.
     */
    private static final String MALFORMED = '''\
ATOM    354 N    TYR A 59A      26.260 -11.444  15.279                     N
ATOM    775 N    GLU A106A      28.000  -5.000  10.000                     N
END
'''

    private static final String COMPLIANT = '''\
ATOM    322 N    HIS A  45      22.567  -7.149  18.452                     N
ATOM    354 N    TYR A  59A     26.260 -11.444  15.279                     N
END
'''

    @Test
    void testRepairShiftsIcodeFromCol26ToCol27() {
        String repaired = repairShiftedInsertionCodes(MALFORMED)
        List<String> lines = repaired.readLines()
        // 2-digit resseq with icode 'A'
        assertEquals('  59', lines[0].substring(22, 26))
        assertEquals('A',    lines[0].substring(26, 27))
        // 3-digit resseq with icode 'A'
        assertEquals(' 106', lines[1].substring(22, 26))
        assertEquals('A',    lines[1].substring(26, 27))
    }

    @Test
    void testRepairIsNoOpOnCompliantInput() {
        // Same instance returned when nothing needs repair (cheap fast path).
        assertSame(COMPLIANT, repairShiftedInsertionCodes(COMPLIANT))
    }

    @Test
    void testRepairedTextParsesViaBioJava() {
        Structure s = cz.siret.prank.utils.PdbUtils.loadFromString(repairShiftedInsertionCodes(MALFORMED))
        assertNotNull(s)
        assertEquals(2, s.chains[0].atomGroups.size())
    }

}
