package cz.siret.prank.domain.loaders.pockets

import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Structure

/**
 * Repairs malformed PUResNet pocket PDB files before parsing.
 *
 * PUResNet's pocket exporter occasionally left-shifts the residue insertion
 * code into PDB column 26 (the last digit of the resSeq integer field)
 * instead of column 27, producing values like " 59A" in cols 23-26 that
 * BioJava's strict parser rejects with NumberFormatException.
 *
 * This class detects the malformed pattern and repairs it in memory, so
 * the standard PdbUtils path used elsewhere in P2Rank is left untouched.
 *
 * <h3>Diagnosis (why we are sure the input is malformed, not the parser)</h3>
 *
 * Cause: PUResNet pocket PDBs put an alpha char at col 26 with col 27 blank
 * — invalid per the PDB spec.
 *
 * Verified at byte level: plain ASCII, LF endings, no tabs; col 26 = 'A',
 * col 27 = ' '.
 *
 * Three independent parsers agree the file is malformed:
 * <ul>
 *   <li>BioJava → NumberFormatException</li>
 *   <li>Biopython (strict and permissive) → both fail</li>
 *   <li>gemmi → loads but silently drops every icode
 *       (data corruption, not a workaround)</li>
 * </ul>
 *
 * After repair: all three parsers load correctly with icodes preserved;
 * unmodified P2Rank 2.5.1 loads the file with no exception.
 *
 * Conclusion: purely a malformed-input problem, not a BioJava or P2Rank
 * config issue.
 */
@Slf4j
@CompileStatic
class PUResNetPdbRepair {

    /**
     * Load a PUResNet pocket PDB. If the file contains malformed shifted
     * insertion codes, repair them in memory before parsing; otherwise the
     * file is parsed unchanged via the standard PdbUtils path.
     */
    static Structure loadPocketStructure(String file) {
        String text = Futils.readPossiblyCompressedFile(file)
        String repaired = repairShiftedInsertionCodes(text)
        if (repaired.is(text)) {
            return PdbUtils.loadFromPdbFile(file)
        }
        log.warn("repaired shifted insertion codes in PUResNet pocket file [{}]", file)
        return PdbUtils.loadFromString(repaired)
    }

    /**
     * Repair PDB ATOM/HETATM lines whose residue insertion code has been
     * written into column 26 instead of column 27 (left-shifted by one
     * column). Returns the original string instance when nothing needs
     * repair.
     *
     * Detection: alpha character at column 26 AND space at column 27 — that
     * combination cannot occur in a spec-compliant ATOM/HETATM record (col 26
     * must be a digit or space; if col 27 is blank, col 26 cannot be alpha).
     *
     * Repair: insert one space at column 23, which right-justifies the
     * integer in cols 23-26 and pushes the icode from col 26 to col 27.
     */
    static String repairShiftedInsertionCodes(String pdbText) {
        String[] lines = pdbText.split('\n', -1)
        boolean changed = false
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i]
            if (lineHasShiftedInsertionCode(line)) {
                lines[i] = line.substring(0, 22) + ' ' + line.substring(22, 26) + line.substring(27)
                changed = true
            }
        }
        return changed ? String.join('\n', lines) : pdbText
    }

    private static boolean lineHasShiftedInsertionCode(String line) {
        return line.length() >= 27 &&
                (line.startsWith('ATOM  ') || line.startsWith('HETATM')) &&
                Character.isLetter(line.charAt(25)) &&
                line.charAt(26) == (' ' as char)
    }

}
