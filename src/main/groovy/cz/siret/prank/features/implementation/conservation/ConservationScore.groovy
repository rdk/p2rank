package cz.siret.prank.features.implementation.conservation

import com.univocity.parsers.tsv.TsvParser
import com.univocity.parsers.tsv.TsvParserSettings
import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.domain.ResidueChain
import cz.siret.prank.domain.labeling.ResidueLabeling
import cz.siret.prank.domain.loaders.ConservationLoader
import cz.siret.prank.export.FastaExporter
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.implementation.conservation.provider.ConservationProvider
import cz.siret.prank.features.implementation.conservation.provider.ConservationProviderFactory
import cz.siret.prank.geom.Struct
import cz.siret.prank.prediction.transformation.ZscoreTpTransformer
import cz.siret.prank.program.P2Rank
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.program.params.Params
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Chain
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.GroupType
import org.biojava.nbio.structure.ResidueNumber

import javax.annotation.Nullable

@Slf4j
@CompileStatic
class ConservationScore implements Parametrized {
    
    /** conservation keys for secondaryData map in Protein class. */
    public static final String CONSERV_LOADED_KEY = "CONSERVATION_LOADED"
    public static final String CONSERV_SCORE_KEY = "CONSERVATION_SCORE"

    private Map<ResidueNumberWrapper, Double> scores

    private ConservationScore(Map<ResidueNumberWrapper, Double> scores) {
        this.scores = scores
    }

    /**
     * Per-chain conservation loading metadata.
     */
    @CompileStatic
    static class ChainConservationInfo {
        final String chainId
        final File scoreFile        // null if not found
        final boolean loaded        // file existed and scores were parsed
        final int matchedResidues   // residues with matched conservation scores
        final int chainResidues     // total AA residues in the chain from structure

        ChainConservationInfo(String chainId, File scoreFile, boolean loaded, int matchedResidues, int chainResidues) {
            this.chainId = chainId
            this.scoreFile = scoreFile
            this.loaded = loaded
            this.matchedResidues = matchedResidues
            this.chainResidues = chainResidues
        }
    }

    /** Per-chain conservation loading metadata. Key: chain authorId */
    private Map<String, ChainConservationInfo> chainInfoMap = Collections.emptyMap()

    Map<String, ChainConservationInfo> getChainInfoMap() {
        return chainInfoMap
    }

    private static class AAScore {
        String chainId
        String letter
        double score
        int index

        AAScore(String chainId, String letter, double score, int index) {
            this.chainId = chainId
            this.letter = letter
            this.score = score
            this.index = index
        }
    }

//===========================================================================================================//

    @Nullable
    private Double getScoreForResidueW(ResidueNumberWrapper residueNum) {
        return scores.get(residueNum)
    }

    @Nullable
    Double getScoreForResidue(ResidueNumber residueNum) {
        return getScoreForResidueW(new ResidueNumberWrapper(residueNum))
    }

    double getScoreForResidueSafe(ResidueNumber residueNum) {
        return getScoreForResidue(residueNum) ?: 0d
    }

//===========================================================================================================//

    ResidueLabeling<Double> toDoubleLabeling(Protein p) {
        ResidueLabeling<Double> labeling = new ResidueLabeling<>(p.residues.size())
        int missing = 0
        for (Residue r : p.residues) {
            Double val = getScoreForResidue(r.residueNumber)
            if (val == null) {
                missing++
                labeling.add(r, 0d)
            } else {
                labeling.add(r, val)
            }
        }
        if (missing > 0) {
            log.warn "Conservation: {} of {} residues have no score and default to 0 [{}]",
                    missing, p.residues.size(), p.name
        }
        return labeling
    }

    Map<ResidueNumberWrapper, Double> getScoreMap() {
        return this.scores
    }

    int size() {
        return this.scores.size()
    }

//===========================================================================================================//

    private ConservationScore zScores = null

    private ConservationScore calculateZScores() {
        List<Double> vals = new ArrayList<>(scores.values())
        ZscoreTpTransformer transformer = new ZscoreTpTransformer()
        transformer.doTrain(vals)

        Map<ResidueNumberWrapper, Double> zscores = new HashMap<>()
        for (Map.Entry<ResidueNumberWrapper, Double> e : scores.entrySet()) {
            double z = transformer.transformScore(e.value)
            zscores.put(e.key, z)
        }
        return new ConservationScore(zscores)
    }

    ConservationScore getZScores() {
        if (zScores == null) {
            zScores = calculateZScores()
        }
        return zScores
    }

//===========================================================================================================//

    static enum ScoreFormat {
        ConCavityFormat,
        JSDFormat
    }

    private static List<AAScore> loadScoreFile(File scoreFile, ScoreFormat format, String chainId) {
        TsvParserSettings settings = new TsvParserSettings()
        settings.setLineSeparatorDetectionEnabled(true)
        TsvParser parser = new TsvParser(settings)
        List<String[]> lines = parser.parseAll(Futils.inputStream(scoreFile))
        List<AAScore> result = new ArrayList<>(lines.size())
        for (String[] line : lines) {
            int index = -1
            double score = 0
            String letter = "-"
            switch (format) {
                case ScoreFormat.ConCavityFormat:
                    index = Integer.parseInt(line[0])
                    letter = line[1]
                    score = Double.parseDouble(line[2])
                    break
                case ScoreFormat.JSDFormat:
                    index = Integer.parseInt(line[0])
                    score = Double.parseDouble(line[1])
                    letter = line[2].substring(0, 1)
                    break
            }
            score = score < 0 ? 0 : score
            if (letter != "-") {
                result.add(new AAScore(chainId, letter, score, index))
            }
        }
        return result
    }

    static ConservationScore loadForProtein(Protein protein, ProcessedItemContext itemContext) {
        return loadForProtein(protein, itemContext, ScoreFormat.JSDFormat)
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

        log.info(mismatchMsg + ". Aligning chains using LCS...")
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
     * Load conservation scores and map them to residues in the given protein structure.
     *
     * @param structure Protein BioJava structure
     * @param scoreFiles Map from chain ids to files
     * @param format Score format (JSD or ConCavity), default: JSD
     * @return new instance of ConservationScore (map from residual numbers to conservation scores)
     */
    static ConservationScore loadForProtein(Protein protein, ProcessedItemContext itemContext, ScoreFormat format) {
        Map<ResidueNumberWrapper, Double> scores = new HashMap<>()

        // TODO use protein.getResidueChains() instead and compare, masked sequences should give better match

        Set<String> residueChainIds = protein.residueChains*.authorId.toSet()
        log.debug "Loading conservation only for residue chains: {}", residueChainIds.toSorted()


        List<Chain> conservationChains = new ArrayList<>()

        for (Chain chain : protein.structure.getChains()) {
            String chainId = Struct.getAuthorId(chain) // authorId == chain letter in old PDB model

            if (!residueChainIds.contains(chainId)) {
                log.debug "Skip chain '{}': not in residueChains", chainId
                continue
            }
            if (chain.getAtomGroups(GroupType.AMINOACID).size() <= 0) {
                log.debug "Skip chain '{}': no amino acid groups", chainId
                continue
            }

            conservationChains.add(chain)
        }

        log.info("loading conservation for {} chains in protein [{}]: {}", conservationChains.size(),
                protein.name, conservationChains.collect { Struct.getAuthorId(it) })

        // Resolve provider once for all chains (singleton with semaphore)
        ConservationProvider provider = ConservationProviderFactory.getOrCreateProvider()

        Map<String, ChainConservationInfo> chainInfo = new LinkedHashMap<>()

        for (Chain chain : conservationChains) {
            String chainId = Struct.getAuthorId(chain)
            // raw authorId keys both Protein.residueChainsByAuthorId (getResidueChain below) and the
            // chainInfoMap that AnalyzeRoutine reads by chain.authorId; mask ONLY for conservation
            // file/cache naming (legacy HMM convention), not for chain lookup or the report map.
            String maskedChainId = Struct.maskEmptyChainId(chainId)
            List<Group> aaGroups = chain.getAtomGroups(GroupType.AMINOACID)
            int chainResidueCount = aaGroups.size()

            try {
                File scoreFile
                if (provider != null) {
                    ResidueChain residueChain = protein.getResidueChain(chainId)
                    if (residueChain == null) {
                        P2Rank.failStatic("No residue chain found for chainId '$chainId' in protein '${protein.name}'", log)
                        chainInfo.put(chainId, new ChainConservationInfo(chainId, null, false, 0, chainResidueCount))
                        continue
                    }

                    // Note: sending masked sequence to provider as per previous convention where hmm conservation
                    //       scores were generated for masked sequences (see https://github.com/cusbg/p2rank-framework/wiki/Large-scale-Predictions).
                    //       We might need to change it in the future for different provider types,
                    //       or move masking to HmmServerConservationProvider.
                    String sequence = FastaExporter.maskFastaChain(residueChain.standardCodeCharString)
                    scoreFile = ConservationLoader.instance.findOrFetchConservationFile(
                        itemContext, protein.fileName, maskedChainId, sequence, provider)
                } else {
                    scoreFile = ConservationLoader.instance.findConservationFile(
                        itemContext, protein.fileName, maskedChainId)
                }

                log.info "Loading conservation scores from file [{}]", scoreFile
                if (scoreFile!=null && scoreFile.exists()) {
                    List<AAScore> chainScores = loadScoreFile(scoreFile, format, chainId)

                    if (log.traceEnabled) {
                        log.trace "loaded chain scores:\n  {}", chainScores.collect { "$it.index $it.letter $it.score" }.join("\n")
                    }

                    int sizeBefore = scores.size()
                    matchSequences(chainId, aaGroups, chainScores, scores)
                    int matched = scores.size() - sizeBefore
                    chainInfo.put(chainId, new ChainConservationInfo(chainId, scoreFile, true, matched, chainResidueCount))
                } else {
                    P2Rank.failStatic("Conservation score file doesn't exist for [protein:$protein.name chain:$chainId] file:[$scoreFile]", log)
                    chainInfo.put(chainId, new ChainConservationInfo(chainId, scoreFile, false, 0, chainResidueCount))
                }
            } catch (Exception e) {
                P2Rank.failStatic("Failed to load conservation file for [protein:$protein.name chain:$chainId]", e, log)
                chainInfo.put(chainId, new ChainConservationInfo(chainId, null, false, 0, chainResidueCount))
            }
        }

        ConservationScore result = new ConservationScore(scores)
        result.chainInfoMap = chainInfo
        return result
    }

}
