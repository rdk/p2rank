package cz.siret.prank.domain.labeling

import cz.siret.prank.domain.*
import cz.siret.prank.program.PrankException
import cz.siret.prank.utils.Cutils
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

    List<LabeledChain> elements
    Map<String, LabeledChain> elementsByCode
    

    private SprintLabelingLoader(List<LabeledChain> elements, String fname) {
        this.elements = elements


        elementsByCode = Cutils.mapWithUniqueIndex(elements, { it.code }, {
            throw new RuntimeException("Duplicate keys in labeling file [${fname}]: " + it)
        })
    }

    @Override
    boolean isBinary() {
        return true
    }

    @Override
    ResidueLabeling<Double> getDoubleLabeling() {
        return null
    }

    String toElementCode(Protein protein, ResidueChain chain) {
//        String protCode = Futils.removeExtension(protein.name)
        String protCode = protein.name.substring(0, 4)
        String chainId = chain.authorId

        return protCode + chainId
    }

    @Override
    ResidueLabeling<Boolean> labelResidues(Residues residues, Protein protein) {

        Map<Residue.Key, Boolean> labelMap = new HashMap<>()



        boolean foundOneChain = false
        for (ResidueChain chain : protein.residueChains) {
            String chainCode = toElementCode(protein, chain)

            LabeledChain element = elementsByCode.get(chainCode)

            if (element == null) {
                log.info "Labels for chain [{}] not found in labeling file", chainCode
                continue
            } else {
                foundOneChain = true
            }

            if (chain.length != element.length) {
                throw new PrankException("Chain lengths do not match for [$chainCode] (structure: $chain.length, labeling file: $element.length)")
            }

            int i = 0
            for (Residue residue : chain.residues) {
                char eleChar = element.chain.charAt(i)

                if (residue.codeCharBiojava != eleChar) {
                    throw new PrankException("Residue code mismatch in [$chainCode] at position [$i] (structure: $residue.codeCharBiojava, labeling file: $eleChar)")
                }

                boolean value = element.labels.charAt(i) != ('0' as char)
                labelMap.put(residue.key, value)
                i++
            }
        }

        if (!foundOneChain) {
            throw new PrankException("Labeling not defined for any chain of the protein [$protein.name]")
        }

        BinaryLabeling result = new BinaryLabeling(residues.count)

        for (Residue residue : residues.list) {
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
        List<LabeledChain> elements = new ArrayList<>()

        for (int i=0; i!=lines.size(); i++) {
            String line = lines[i]
            if (line.startsWith(">")) {
                String code = line.substring(1)
                String chain = lines[i+1]
                String labels = lines[i+2]

                elements.add new LabeledChain(code, chain, labels)
            }
        }

        return new SprintLabelingLoader(elements, fname)
    }

    /**
     * Bin of Sprint labeling file representing one chain
     */
    static class LabeledChain {
        /** 4-character lower case pdb code + uppercase chain id (one char) */
        String code
        /** single letter residue codes */
        String chain
        /** string of 0 and 1 */
        String labels

        LabeledChain(String code, String chain, String labels) {
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
