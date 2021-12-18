package cz.siret.prank.features.implementation.structmotif;

import cz.siret.prank.domain.Residue;
import cz.siret.prank.program.PrankException;
import cz.siret.prank.utils.PerfUtils;
import cz.siret.prank.utils.Sutils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class ResidueMotif {

    private static final Logger log = LoggerFactory.getLogger(ResidueMotif.class);

    private final String code;
    /**
     * Compiled representation for matching.
     * Sorted one letter AA codes, digits expanded.
     */
    private final String compiledCode;


    public ResidueMotif(String code, String compiledCode) {
        this.code = code;
        this.compiledCode = compiledCode;
    }

    public boolean matches(List<Residue> residues) {
        return matches(toSortedOneLetterCodes(residues));
    }

    /**
     * @param sortedResidueCodes sorted residue one letter AA codes
     */
    public boolean matches(String sortedResidueCodes) {

        boolean res = PerfUtils.coversWithBreaks(sortedResidueCodes, compiledCode);

        //if (res) {
        //    log.warn("Motif '{}/{}' matching residues with codes '{}': {}", code, compiledCode, sortedResidueCodes, res);
        //}

        return res;
    }


    public String getCode() {
        return code;
    }


    public String getCompiledCode() {
        return compiledCode;
    }

    public int getSize() {
        return compiledCode.length();
    }

//===============================================================================================//

    public static String toSortedOneLetterCodes(List<Residue> residues) {
        char[] chars = new char[residues.size()];

        int i = 0;
        for (Residue residue : residues) {
            chars[i] = residue.getCodeCharStandard();
            i++;
        }

        Arrays.sort(chars);

        return new String(chars);
    }

    public static ResidueMotif parse(String code) {
        StringBuilder compiled = new StringBuilder();

        if (code.length() == 0) {
            throw new PrankException("Invalid structural motif code: " + code);
        }

        for (int i = 0; i != code.length(); ++i) {
            char c = code.charAt(i);
            if (!isValidChar(c)) {
                throw new PrankException("Invalid structural motif code: " + code);
            }
            if (Character.isDigit(c)) {
                if (i == 0) {
                    throw new PrankException("Invalid structural motif code: " + code);
                }
                char prev = code.charAt(i-1);
                if (Character.isDigit(prev)) {
                    throw new PrankException("Invalid structural motif code: " + code);
                }
                int k = Integer.parseInt(""+c);
                compiled.append(StringUtils.repeat(prev, k - 1));
            } else {
                compiled.append(c);
            }
        }

        String res = Sutils.sortString(compiled.toString());

        return new ResidueMotif(code, res);
    }

    private static boolean isValidChar(char c) {
        if (Character.isDigit(c) && c != '0') return true;
        if (Character.isLetter(c) && Character.isUpperCase(c)) return true;
        return false;
    }

}
