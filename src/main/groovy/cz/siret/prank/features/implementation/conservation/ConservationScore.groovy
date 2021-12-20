package cz.siret.prank.features.implementation.conservation

import com.univocity.parsers.tsv.TsvParser
import com.univocity.parsers.tsv.TsvParserSettings
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.labeling.ResidueLabeling
import cz.siret.prank.export.FastaExporter
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.P2Rank
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.PdbUtils
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
        Double res = this.scores.get(residueNum);
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
        return this.scores;
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
     * @param scores Parsed conservation scores.
     * @param outResult Add matched scores to map (residual number -> conservation score)
     */
    static void matchSequences(String chainId, List<Group> chain, List<AAScore> scores,
                               Map<ResidueNumberWrapper, Double> outResult) {
        log.info "Matching pdb chain $chainId (n={}) with score chain (n={})", chain.size(), scores.size()

        // Check if the strings match
        String pdbChain = chain.collect { group -> PdbUtils.getStandardOneLetterCode(group) }.join("")
        String scoreChain = scores.collect { ch -> ch.letter.toUpperCase() }.join("")

        log.info "chain $chainId in structure: {}", pdbChain
        log.info "chain $chainId in scoreFile: {}", scoreChain

        pdbChain = FastaExporter.maskFastaChain(pdbChain)
        scoreChain = FastaExporter.maskFastaChain(scoreChain) // note '-' are ignored when loading scoreChain

        log.info "masked chain $chainId in structure: {}", pdbChain
        log.info "masked chain $chainId in scoreFile: {}", scoreChain


        if (pdbChain.equals(scoreChain)) {  // exact match
            log.info("Exact score sequence match")
            for (int i = 0; i < scores.size(); i++) {
                outResult.put(new ResidueNumberWrapper(chain.get(i).getResidueNumber()), scores.get(i).score)
            }
            return
        }

        String mismatchMsg = "Score sequence for chain $chainId doesn't match exactly"
        if (Params.inst.fail_on_conserv_seq_mismatch) { // conditionally fail
            P2Rank.failStatic(mismatchMsg, log)
        }

        log.info(mismatchMsg + " Aligning chains using LCS")
        int[][] lcs = calcLongestCommonSubSequence(pdbChain, scoreChain);

        Map<ResidueNumberWrapper, Double> result = matchUsingLcs(chain, scores, pdbChain, scoreChain, lcs)

        log.info("Score matched for {} residues", result.size())

        outResult.putAll(result)
    }

    private static Map<ResidueNumberWrapper, Double> matchUsingLcs(List<Group> chain, List<AAScore> scores, String pdbChain, String scoreChain, int[][] lcs) {
        Map<ResidueNumberWrapper, Double> result = new HashMap<>()

        // debug strings
        StringBuilder sCommom = new StringBuilder(scoreChain.length())
        StringBuilder sScore = new StringBuilder(scoreChain.length())
        StringBuilder sPdb = new StringBuilder(pdbChain.length())

        // Backtrack the actual sequence.
        int i = chain.size(), j = scores.size();
        while (i > 0 && j > 0) {
            if (pdbChain.charAt(i - 1) == scoreChain.charAt(j - 1)) {  // Letters are equal.
                result.put(new ResidueNumberWrapper(chain.get(i - 1).getResidueNumber()),
                        scores.get(j - 1).score)

                char c = pdbChain.charAt(i - 1)
                sCommom.append(c)
                sScore.append(c)
                sPdb.append(c)

                i--;
                j--;
            } else {
                if (lcs[i][j - 1] > lcs[i - 1][j]) {
                    sScore.append(scoreChain.charAt(j - 1))
                    sPdb.append("-")
                    sCommom.append("-")

                    j--;
                } else {
                    sPdb.append(pdbChain.charAt(i - 1))
                    sScore.append("-")
                    sCommom.append("-")

                    i--;
                }
            }
        }


        if (log.isInfoEnabled()) {
            log.info "matchSequences/common: " + sCommom.toString().reverse()
            log.info "matchSequences/pdb   : " + sPdb.toString().reverse()
            log.info "matchSequences/score : " + sScore.toString().reverse()
        }

        return result
    }

    static int[][] calcLongestCommonSubSequence(String pdbChain, String scoreChian) {
        // Implementation of Longest Common SubSequence
        // https://en.wikipedia.org/wiki/Longest_common_subsequence_problem
        int[][] lcs = new int[pdbChain.size() + 1][scoreChian.size() + 1];
        for (int i = 0; i <= pdbChain.size(); i++) lcs[i][0] = 0;
        for (int j = 0; j <= scoreChian.size(); j++) lcs[0][j] = 0;
        for (int i = 1; i <= pdbChain.size(); i++) {
            for (int j = 1; j <= scoreChian.size(); j++) {
                // Letters are equal.
                if (pdbChain.charAt(i - 1) == scoreChian.charAt(j - 1)) {
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
            String chainId = Struct.getAuthorId(chain) // authorId == chain letter in old PDB model
            if (chain.getAtomGroups(GroupType.AMINOACID).size() <= 0) {
                log.debug "Skip chain '{}': no amino acids", chainId
                continue // skip non-amino acid chains
            }
            chainId = Struct.maskEmptyChainId(chainId)

            List<AAScore> chainScores = null
            try {
                File scoreFile = scoreFiles.apply(chainId)
                log.info "Loading conservation scores from file [{}]", scoreFile
                if (scoreFile!=null && scoreFile.exists()) {
                    chainScores = ConservationScore.loadScoreFile(scoreFile, format)

                    log.trace "loaded chain scores:\n" +
                            chainScores.collect { "$it.index $it.letter $it.score" }.join("\n")

                    matchSequences(chainId, chain.getAtomGroups(GroupType.AMINOACID), chainScores, scores)
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