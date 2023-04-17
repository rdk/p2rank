package cz.siret.prank.domain.labeling

import cz.siret.prank.domain.Dataset
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.Residues
import cz.siret.prank.program.PrankException
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Sutils
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils

/**
 * Loads labeling explicitly defined in dataset column "predicted_labeling" for each item.
 * Intended to load labelings predicted by other methods.
 *
 * So far only Vorffip (peptide binding site) method is supported.
 */
@Slf4j
@CompileStatic
abstract class StaticResidueLabeler extends ResidueLabeler<Boolean> implements Writable {

    static StaticResidueLabeler createForDatasetItem(Dataset.Item item) {
        String method = item.originDataset.attributes.get(Dataset.PARAM_PREDICTION_METHOD)

        if ("vorffip" == method) {
            String path = item.columnValues.get('predicted_labeling')
            path = item.originDataset.dir + '/' + path

            return new VorffipPredictedLabelingLoader(path)
        } else {
            throw new PrankException("Invalid method param in dataset: " + method)
        }
    }

    @CompileStatic
    static class VorffipPredictedLabelingLoader extends StaticResidueLabeler implements Writable {

        ResidueLabeling<Double> doubleLabeling
        String path

        VorffipPredictedLabelingLoader(String path) {
            this.path = path
        }

        Double parseScore(String s) {
            try {
                return Double.valueOf(s)
            } catch (Exception e) {
                throw new PrankException("Failed to parse score " + s)
            }
        }

        @Override
        ResidueLabeling<Boolean> labelResidues(Residues residues, Protein protein) {

            Map<String, Double> scores = new HashMap<>()
            for (String line : Futils.readLines(path).tail()) {
                if (StringUtils.isBlank(line)) continue

                def cols = Sutils.splitOnWhitespace(line)
                String chain = cols[0]
                String resnum = cols[1]
                Double score = parseScore(cols[3])

                String code = chain + '_' + resnum
                scores.put(code, score)
            }

            int loaded = 0
            doubleLabeling = new ResidueLabeling<Double>(residues.count)
            for (Residue res : residues) {
                String code = res.chain.authorId + '_' + res.residueNumber.seqNum + (res.residueNumber.insCode ?: "")
                Double score = scores.get(code)

                if (score==null) {
                    log.error("Could not find static labeling for: {}", code)
                    score = 0
                } else {
                    loaded++
                }

                doubleLabeling.add(res, score)
            }

            BinaryLabeling resLabels = new BinaryLabeling(residues.count)
            for (LabeledResidue<Double> it : doubleLabeling.labeledResidues) {
                resLabels.add(it.residue, binaryLabel(it.label))
            }

            write "Loaded $loaded/${residues.count} predicted residue labels"

            return resLabels
        }

        private boolean binaryLabel(double score) {
            score >= 0.5
        }

        @Override
        boolean isBinary() {
            return true
        }

        @Override
        ResidueLabeling<Double> getDoubleLabeling() {
            return doubleLabeling
        }
        
    }

}
