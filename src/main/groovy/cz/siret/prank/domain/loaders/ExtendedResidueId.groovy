package cz.siret.prank.domain.loaders

import cz.siret.prank.program.PrankException
import cz.siret.prank.utils.Sutils
import org.biojava.nbio.structure.ResidueNumber

import javax.annotation.Nonnull
import javax.annotation.Nullable

/**
 * Residue id with optional one letter AA code and optional insertion code.
 *
 * Format:
 * A_D160A
 * A_160A
 * A_160
 */
class ExtendedResidueId {

    @Nonnull
    String chain
    int seqNum

    @Nullable
    Character insCode
    
    /**
     * one letter A code
     */
    @Nullable
    Character aaCode

    ExtendedResidueId(String chain, int seqNum, Character insCode, Character aaCode) {
        this.chain = chain
        this.aaCode = aaCode
        this.seqNum = seqNum
        this.insCode = insCode
    }

    /**
     * Standard residue id (without one letter AA code)
     * Format: A_160A
     */
    String toStandardResidueId() {
        return chain + "_" + seqNum + ((insCode!=null) ? insCode : "")
    }

    ResidueNumber toResidueNumber() {
        return new ResidueNumber(chain, seqNum, insCode)
    }

    static ExtendedResidueId parse(String idStr) {
        try {
            String str = idStr.trim()

            def split = Sutils.split(str, "_")

            if (split.size() != 2) {
                throw new PrankException("Invalid count of _ characters in residue id '$idStr'")
            }

            String chain = split[0]
            String rest = split[1]
            Character aaCode = null
            int seqNum = -1
            Character insCode = null

            if (!Character.isDigit(rest.charAt(0))) {
                aaCode = rest.charAt(0)
                rest = rest.substring(1, rest.size())
            }

            String resNumStr = Sutils.digitsPrefix(rest)
            seqNum = Integer.parseInt(resNumStr)

            String insCodeStr = Sutils.removePrefix(rest, resNumStr)
            if (insCodeStr.empty) {
                insCode = null
            } else {
                insCode = insCodeStr[0] as char
            }
            if (insCodeStr.size() > 1) {
                new PrankException("Invalid ins. code: '$insCodeStr'")
            }

            return new ExtendedResidueId(chain, seqNum, insCode, aaCode)
        } catch (Exception e) {
            throw new PrankException("Failed to parse residue id '$idStr'", e)
        }
    }

}
