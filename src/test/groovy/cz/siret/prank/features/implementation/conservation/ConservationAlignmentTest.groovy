package cz.siret.prank.features.implementation.conservation

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests the LCS-based conservation sequence alignment.
 * Catches off-by-one bugs in the LCS backtracking that would
 * silently shift conservation values to wrong residues.
 */
@CompileStatic
class ConservationAlignmentTest {

    @Test
    void lcsExactMatch() {
        int[][] lcs = ConservationScore.calcLongestCommonSubSequence("ACDEF", "ACDEF")
        assertEquals(5, lcs[5][5], "exact match LCS length should be 5")
    }

    @Test
    void lcsWithInsertionInScore() {
        // PDB: ACDEF, Score: AXCDEF (extra X inserted)
        int[][] lcs = ConservationScore.calcLongestCommonSubSequence("ACDEF", "AXCDEF")
        assertEquals(5, lcs[5][6], "LCS should match all 5 PDB residues")
    }

    @Test
    void lcsWithDeletionInScore() {
        // PDB: ACDEF, Score: ADEF (C missing from score)
        int[][] lcs = ConservationScore.calcLongestCommonSubSequence("ACDEF", "ADEF")
        assertEquals(4, lcs[5][4], "LCS should match 4 of 5 PDB residues")
    }

    @Test
    void lcsWithMismatch() {
        // PDB: ACDEF, Score: AXDEY (C->X, F->Y)
        int[][] lcs = ConservationScore.calcLongestCommonSubSequence("ACDEF", "AXDEY")
        assertEquals(3, lcs[5][5], "LCS should match A, D, E")
    }

    @Test
    void lcsEmptyStrings() {
        int[][] lcs = ConservationScore.calcLongestCommonSubSequence("", "ACDEF")
        assertEquals(0, lcs[0][5], "empty PDB chain -> LCS=0")

        int[][] lcs2 = ConservationScore.calcLongestCommonSubSequence("ACDEF", "")
        assertEquals(0, lcs2[5][0], "empty score chain -> LCS=0")
    }

    @Test
    void lcsSingleCharMatch() {
        int[][] lcs = ConservationScore.calcLongestCommonSubSequence("A", "A")
        assertEquals(1, lcs[1][1])
    }

    @Test
    void lcsSingleCharMismatch() {
        int[][] lcs = ConservationScore.calcLongestCommonSubSequence("A", "X")
        assertEquals(0, lcs[1][1])
    }

    @Test
    void lcsLongerSequencesPreserveOrder() {
        // a realistic-ish short example: score has extra residues at start and end
        String pdb   = "MKLVTG"
        String score = "XMKLVTGY"
        int[][] lcs = ConservationScore.calcLongestCommonSubSequence(pdb, score)
        assertEquals(6, lcs[pdb.length()][score.length()], "all PDB residues should match")
    }

    @Test
    void lcsSymmetricLength() {
        String a = "ACDEF"
        String b = "AXXEF"
        int[][] lcsAB = ConservationScore.calcLongestCommonSubSequence(a, b)
        int[][] lcsBA = ConservationScore.calcLongestCommonSubSequence(b, a)
        assertEquals(lcsAB[a.length()][b.length()], lcsBA[b.length()][a.length()],
                "LCS length should be symmetric")
    }
}
