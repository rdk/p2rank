package cz.siret.prank.export


import cz.siret.prank.domain.ResidueChain
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Structure

import javax.annotation.Nullable

/**
 *
 */
@Slf4j
@CompileStatic
class FastaExporter {

    static FastaExporter getInstance() {
        return new FastaExporter()
    }

    String makeFastaHeader(ResidueChain chain, @Nullable Structure structure) {
        String chainName = chain.authorId
        if (structure?.getPDBCode() == null) {
            return ">chain|" + chainName
        }
        return ">pdb|" + structure.getPDBCode() + "|chain|" + chainName
    }

    String getFastaChainRaw(ResidueChain chain) {
        return chain.codeCharString
    }

    String getFastaChainMasked(ResidueChain chain) {
        return maskFastaChain(getFastaChainRaw(chain))
    }

    String getFastaChain(ResidueChain chain, boolean masked) {
        if (masked) {
            return getFastaChainMasked(chain)
        } else {
            return getFastaChainRaw(chain)
        }
    }

    String formatFastaFile(String header, String chain) {
        return header + "\n" + chain
    }

//===========================================================================================================//

    /**
     * Mask single letter residue code chain.
     * Non letters are replaced by 'X'.
     */
    public static String maskFastaChain(String chain) {
        return chain.getChars().collect { maskResidueCode(it as char) }.join("")
    }

    public static char maskResidueCode(char code) {
        if (!code.isLetter()) {
            code = 'X' as char
        }
        return code
    }

}
