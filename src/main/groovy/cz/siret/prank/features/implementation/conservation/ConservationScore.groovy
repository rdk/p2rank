package cz.siret.prank.features.implementation.conservation

import com.univocity.parsers.tsv.TsvParser
import com.univocity.parsers.tsv.TsvParserSettings
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.labeling.ResidueLabeling
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.P2Rank
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.*

import java.util.function.Function

@Slf4j
@CompileStatic
class ConservationScore implements Parametrized {
    /** conservation keys for secondaryData map in Protein class. */
    public static final String CONSERV_LOADED_KEY = "CONSERVATION_LOADED"
    public static final String CONSERV_SCORE_KEY = "CONSERVATION_SCORE"
    public static final String CONSERV_PATH_FUNCTION_KEY = "CONSERVATION_PATH_FUNCTION"

    private Map<ResidueNumberWrapper, Double> scores;

    private ConservationScore(Map<ResidueNumberWrapper, Double> scores) {
        this.scores = scores;
    }

    private static class AAScore {
        public String letter;
        public double score;
        public int index;

        AAScore(String letter, double score, int index) {
            this.letter = letter;
            this.score = score;
            this.index = index;
        }
    }

    double getScoreForResidue(ResidueNumber residueNum) {
        return getScoreForResidue(new ResidueNumberWrapper(residueNum));
    }

    double getScoreForResidue(ResidueNumberWrapper residueNum) {
        Double res = scores.get(residueNum);
        if (res == null) {
            return 0;
        } else {
            return res.doubleValue();
        }
    }

    ResidueLabeling<Double> toDoubleLabeling(Protein p) {
        ResidueLabeling<Double> labeling = new ResidueLabeling<>(p.residues.size())
        for (Residue r : p.residues) {
            labeling.add(r, getScoreForResidue(r.residueNumber))
        }
        return labeling
    }

    Map<ResidueNumberWrapper, Double> getScoreMap() {
        return scores;
    }

    int size() {
        return this.scores.size();
    }

    static enum ScoreFormat {
        ConCavityFormat,
        JSDFormat
    }

    private static List<AAScore> loadScoreFile(File scoreFile, ScoreFormat format) {
        TsvParserSettings settings = new TsvParserSettings();
        settings.setLineSeparatorDetectionEnabled(true);
        TsvParser parser = new TsvParser(settings);
        List<String[]> lines = parser.parseAll(Futils.inputStream(scoreFile));
        List<AAScore> result = new ArrayList<>(lines.size());
        for (String[] line : lines) {
            int index = -1;
            double score = 0;
            String letter = "-";
            switch (format) {
                case ScoreFormat.ConCavityFormat:
                    index = Integer.parseInt(line[0]);
                    letter = line[1];
                    score = Double.parseDouble(line[2]);
                    break;
                case ScoreFormat.JSDFormat:
                    index = Integer.parseInt(line[0]);
                    score = Double.parseDouble(line[1]);
                    letter = line[2].substring(0, 1);
                    break;
            }
            score = score < 0 ? 0 : score;
            if (letter != "-") {
                result.add(new AAScore(letter, score, index));
            }
        }
        return result;
    }

    static ConservationScore fromFiles(Structure structure,
                                       Function<String, File> scoresFiles)
            throws FileNotFoundException {
        return fromFiles(structure, scoresFiles, ScoreFormat.JSDFormat);
    }

    /**
     * @param chain Chain from PDB Structure
     * @param chainScores Parse conservation scores.
     * @param outResult Add matched scores to map (residual number -> conservation score)
     */
    static void matchSequences(List<Group> chain, List<AAScore> chainScores,
                               Map<ResidueNumberWrapper, Double> outResult) {
        // Check if the strings match
        String pdbChain = chain.collect { ch -> ch.getChemComp().getOne_letter_code().toUpperCase() }.join("")
        String scoreChain = chainScores.collect { ch -> ch.letter.toUpperCase() }.join("")

        if (pdbChain.equals(scoreChain)) {
            for (int i = 0; i < chainScores.size(); i++) {
                outResult.put(new ResidueNumberWrapper(chain.get(i).getResidueNumber()),
                        chainScores.get(i).score);
            }
            return;
        }

        log.info("Matching chains using LCS")
        int[][] lcs = calcLongestCommonSubSequence(chain, chainScores);

        // Backtrack the actual sequence.
        int i = chain.size(), j = chainScores.size();
        while (i > 0 && j > 0) {
            // Letters are equal.
            if (chain.get(i - 1).getChemComp().getOne_letter_code().toUpperCase().equals(
                    chainScores.get(j - 1).letter.toUpperCase())) {
                outResult.put(new ResidueNumberWrapper(chain.get(i - 1).getResidueNumber()),
                        chainScores.get(j - 1).score);
                i--;
                j--;
            } else {
                if (lcs[i][j - 1] > lcs[i - 1][j]) {
                    j--;
                } else {
                    i--;
                }
            }
        }
    }

    static int[][] calcLongestCommonSubSequence(List<Group> chain, List<AAScore> chainScores) {
        // Implementation of Longest Common SubSequence
        // https://en.wikipedia.org/wiki/Longest_common_subsequence_problem
        int[][] lcs = new int[chain.size() + 1][chainScores.size() + 1];
        for (int i = 0; i <= chain.size(); i++) lcs[i][0] = 0;
        for (int j = 0; j <= chainScores.size(); j++) lcs[0][j] = 0;
        for (int i = 1; i <= chain.size(); i++) {
            for (int j = 1; j <= chainScores.size(); j++) {
                // Letters are equal.
                if (chain.get(i - 1).getChemComp().getOne_letter_code().toUpperCase().equals(
                        chainScores.get(j - 1).letter.toUpperCase())) {
                    lcs[i][j] = lcs[i - 1][j - 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i - 1][j], lcs[i][j - 1]);
                }
            }
        }
        return lcs;
    }

    /**
     * Parses conservation scores created from HSSP database and Jensen-Shannon divergence.
     *
     * @param structure Protein BioJava structure
     * @param scoreFiles Map from chain ids to files
     * @param format Score format (JSD or ConCavity), default: JSD
     * @return new instance of ConservationScore (map from residual numbers to conservation scores)
     */
    static ConservationScore fromFiles(Structure structure,
                                       Function<String, File> scoreFiles,
                                       ScoreFormat format) throws FileNotFoundException {
        Map<ResidueNumberWrapper, Double> scores = new HashMap<>()

        for (Chain chain : structure.getChains()) {
            if (chain.getAtomGroups(GroupType.AMINOACID).size() <= 0) {
                continue
            }
            String chainId = Struct.getAuthorId(chain) // authorId == chain letter in old PDB model
            chainId = chainId.trim().isEmpty() ? "A" : chainId   // TODO review. are there ever chains with no id?
            List<AAScore> chainScores = null
            try {
                File scoreFile = scoreFiles.apply(chainId)
                log.info "Loading conservation scores from file [{}]", scoreFile
                if (scoreFile!=null && scoreFile.exists()) {
                    chainScores = ConservationScore.loadScoreFile(scoreFile, format)

                    log.debug "loaded chain scores:\n" +
                            chainScores.collect { "$it.index $it.letter $it.score" }.join("\n")

                    matchSequences(chain.getAtomGroups(GroupType.AMINOACID), chainScores, scores)
                } else {
                    P2Rank.failStatic("Conservation score file doesn't exist for [protein:$structure.name chain:$chainId] file:[$scoreFile]", log)

                }

            } catch (Exception e) {
                P2Rank.failStatic("Failed to load conservation file for [protein:$structure.name chain:$chainId]", e, log)
            }
        }
        return new ConservationScore(scores)
    }

}