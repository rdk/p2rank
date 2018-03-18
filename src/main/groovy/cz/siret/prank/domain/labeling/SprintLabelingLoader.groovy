package cz.siret.prank.domain.labeling

import com.google.common.collect.Maps
import cz.siret.prank.domain.AA
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.ResidueChain
import cz.siret.prank.program.PrankException
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Binary residue labeling loader from file with SPRINT-Str dataset format
 */
@Slf4j
@CompileStatic
class SprintLabelingLoader extends ResidueLabeler<Boolean> implements Writable {

    List<Element> elements
    Map<String, Element> elementsByCode
    

    private SprintLabelingLoader(List<Element> elements) {
        this.elements = elements

        elementsByCode = Maps.uniqueIndex(elements, { it.code })
    }

    @Override
    boolean isBinary() {
        return true
    }

    String toElementCode(Protein protein, ResidueChain chain) {
        String protCode = Futils.removeExtension(protein.name)
        String chainId = chain.id

        return protCode + chainId
    }

    @Override
    ResidueLabeling<Boolean> labelResidues(List<Residue> residues, Protein protein) {

        Map<Residue.Key, Boolean> labelMap = new HashMap<>()



        boolean foundOneChain = false
        for (ResidueChain chain : protein.residueChains) {
            String chainCode = toElementCode(protein, chain)

            Element element = elementsByCode.get(chainCode)

            if (element == null) {
                log.info "Labels for chain [{}] not found in labeling file", chainCode
                continue
            } else {
                foundOneChain = true
            }

            if (chain.size != element.length) {
                throw new PrankException("Chain lengths do not match for [$chainCode] (structure: $chain.size, labeling file: $element.length)")
            }

            int i = 0
            for (Residue residue : chain.residues) {
                char eleChar = element.chain.charAt(i)

                if (residue.codeChar != eleChar) {
                    throw new PrankException("Residue code mismatch in [$chainCode] at position [$i] (structure: $residue.codeChar, labeling file: $eleChar)")
                }

                boolean value = element.labels.charAt(i) != ('0' as char)
                labelMap.put(residue.key, value)
                i++
            }
        }

        if (!foundOneChain) {
            throw new PrankException("Labeling not defined for any chain of protein [$protein.name]")
        }

        BinaryResidueLabeling result = new BinaryResidueLabeling()

        for (Residue residue : residues) {
            result.add(residue, labelMap.get(residue.key))
        }

        // TODO check if all residues have been assigned non null label 

        return result
    }

    /**
     *
     * for the format see p2rank-datasets2/peptides/sprint17/test_labels.txt
     * @param fname
     * @return
     */
    static SprintLabelingLoader loadFromFile(String fname) {
        List<String> lines = Futils.readLines(fname)
        List<Element> elements = new ArrayList<>()

        for (int i=0; i!=lines.size(); i++) {
            String line = lines[i]
            if (line.startsWith(">")) {
                String code = line.substring(1)
                String chain = lines[i+1]
                String labels = lines[i+2]

                elements.add new Element(code, chain, labels)
            }
        }

        return new SprintLabelingLoader(elements)
    }

    /**
     * Element of Sprint labeling file representing one chain
     */
    private static class Element {
        /** 4-character lower case pdb code + uppercase chain id (one char) */
        String code
        /** single letter residue codes */
        String chain
        /** string of 0 and 1 */
        String labels

        Element(String code, String chain, String labels) {
            this.code = code
            this.chain = chain
            this.labels = labels

            checkValidity()
        }

        int getLength() {
            chain.length()
        }

        void checkValidity() {
            assert chain.size() == labels.size()
            assert labels =~ /[01]*/
            assert chain =~ /[${AA.ALL_CODE_CHARS}]*/
        }
    }

}
