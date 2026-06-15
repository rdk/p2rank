package cz.siret.prank.geom

import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Chain
import org.biojava.nbio.structure.Structure

/**
 * Detects and collapses <b>alternate-conformation chains</b>: microheterogeneity deposited as separate whole
 * chains that are each (almost) entirely tagged with a single non-blank altLoc letter and that geometrically
 * superimpose on a primary chain.
 *
 * <p>Motivating case: PDB <b>6een</b> ("...microheterogeneity"), whose chains A/B/C/D are the same polymer in
 * four conformations sitting ~0.002 A apart (a second polymer E/F/G/H is split the same way). BioJava loads
 * all of them, so P2Rank surfaces ~4x the atoms it should, which inflates SAS point counts, per-residue
 * scores and pocket counts (and makes the {@code packed_distinct_*} surface strategies, which keep
 * near-duplicate points, diverge from {@code faster}+sparsify).
 *
 * <p>This is distinct from <b>ordinary within-residue altLocs</b> (e.g. one side chain modeled in two
 * positions): those are already collapsed to a single conformation by the BioJava parser (the alternates live
 * in {@code Group.getAltLocs()}, which P2Rank never reads), so they are left untouched here.
 *
 * <p>Reduction rule: a chain is a <i>candidate</i> when at least {@link #MIN_ALTLOC_FRACTION} of its atoms
 * share one and the same non-blank altLoc letter. Candidates are considered in (altLoc letter, chain id)
 * order; within each cluster of mutually-overlapping candidates the lowest-letter conformation (or a
 * blank-altLoc / non-candidate chain) is the primary and is kept, later overlapping copies are dropped. The
 * geometric overlap test ({@link #MIN_OVERLAP_FRACTION} of the candidate's atoms within
 * {@link #OVERLAP_DISTANCE} of a kept chain) is the safety guard: genuine homo-oligomer copies occupy
 * distinct space, never overlap, and are always kept; only truly redundant superimposed copies are removed.
 *
 * <p>Atom/Chain/Group objects of the kept chains are reused by reference (no cloning), so downstream
 * reference-equality on atoms is preserved.
 */
@Slf4j
@CompileStatic
class AlternateChainReducer {

    /** A chain is a uniform-altLoc (alternate-conformation) candidate when at least this fraction of its
        atoms carry one and the same non-blank altLoc letter (6een chains are 1.0; ordinary chains with a few
        within-residue altLoc atoms are far below this and are treated as primary). */
    static final double MIN_ALTLOC_FRACTION = 0.5d

    /** A candidate chain is considered superimposed on a kept chain when at least {@link #MIN_OVERLAP_FRACTION}
        of its atoms lie within this distance (A) of a kept-chain atom. Alternate copies sit ~0.002 A apart
        (6een: p90 0.13 A, max 1.8 A); genuine separate copies tile distinct space and fall far below. */
    static final double OVERLAP_DISTANCE = 2.0d
    static final double MIN_OVERLAP_FRACTION = 0.7d

    private static final char BLANK_ALTLOC = ' ' as char

    @CompileStatic
    private static class ChainInfo {
        final Chain chain
        final Atoms atoms
        final Character altLoc      // single non-blank altLoc letter, or null when not a uniform-altLoc chain

        ChainInfo(Chain chain, Atoms atoms, Character altLoc) {
            this.chain = chain
            this.atoms = atoms
            this.altLoc = altLoc
        }

        boolean isCandidate() { altLoc != null }
    }

    /**
     * Returns a Structure with redundant alternate-conformation chains removed, or the same Structure
     * unchanged when none are found.
     */
    static Structure reduceAlternateConformationChains(Structure structure, String name) {
        List<Chain> chains = structure.getChains()
        if (chains == null || chains.size() < 2) {
            return structure
        }

        List<ChainInfo> infos = new ArrayList<>(chains.size())
        for (Chain ch : chains) {
            Atoms atoms = Atoms.allFromChain(ch)
            infos.add(new ChainInfo(ch, atoms, uniformAltLoc(atoms)))
        }

        // Non-candidate chains (blank altLoc, water, ligands, ordinary within-residue altLocs) are always kept
        // and serve as overlap references. Candidates are considered in (altLoc, chainId) order: the
        // lowest-letter conformation in each overlapping cluster is kept, later (higher-letter) overlapping
        // copies are dropped. Strictly-lower-letter comparison guarantees same-letter chains are never
        // collapsed against each other (two chains tagged with the same letter are distinct molecules).
        List<ChainInfo> references = new ArrayList<>()
        List<ChainInfo> candidates = new ArrayList<>()
        for (ChainInfo info : infos) {
            (info.candidate ? candidates : references).add(info)
        }

        candidates.sort(new Comparator<ChainInfo>() {
            int compare(ChainInfo a, ChainInfo b) {
                int c = a.altLoc.compareTo(b.altLoc)
                return c != 0 ? c : chainId(a.chain).compareTo(chainId(b.chain))
            }
        })

        List<ChainInfo> keptCandidates = new ArrayList<>()
        List<ChainInfo> removed = new ArrayList<>()
        for (ChainInfo cand : candidates) {
            List<Atoms> refAtoms = new ArrayList<>()
            for (ChainInfo r : references) {
                refAtoms.add(r.atoms)                                  // blank / water / ligand chains
            }
            for (ChainInfo r : keptCandidates) {
                if (r.altLoc.compareTo(cand.altLoc) < 0) {
                    refAtoms.add(r.atoms)                              // lower-letter kept conformations
                }
            }
            if (overlaps(cand.atoms, refAtoms)) {
                removed.add(cand)
            } else {
                keptCandidates.add(cand)
            }
        }

        if (removed.isEmpty()) {
            return structure
        }

        Set<Chain> removedChains = Collections.newSetFromMap(new IdentityHashMap<Chain, Boolean>())
        for (ChainInfo r : removed) {
            removedChains.add(r.chain)
        }
        List<Chain> keptChains = new ArrayList<>()
        for (Chain ch : chains) {
            if (!removedChains.contains(ch)) {
                keptChains.add(ch)
            }
        }

        log.info "reduced {} alternate-conformation chain(s) {} in [{}] ({} -> {} chains)",
                removed.size(), removed.collect { "${chainId(it.chain)}:${it.altLoc}".toString() }, name,
                chains.size(), keptChains.size()

        return PdbUtils.structureWithChains(structure, keptChains)
    }

    /** The single non-blank altLoc letter shared by at least {@link #MIN_ALTLOC_FRACTION} of the chain's
        atoms, or null when the chain is blank, has mixed (within-residue) altLocs, or is below the fraction. */
    private static Character uniformAltLoc(Atoms atoms) {
        int total = atoms.count
        if (total == 0) {
            return null
        }
        Character letter = null
        int nonblank = 0
        for (Atom a : atoms) {
            Character al = a.getAltLoc()
            if (al == null || al.charValue() == BLANK_ALTLOC) {
                continue
            }
            if (letter == null) {
                letter = al
            } else if (letter.charValue() != al.charValue()) {
                return null      // more than one altLoc letter -> within-residue altLocs, not an alternate chain
            }
            nonblank++
        }
        if (letter == null) {
            return null
        }
        return (nonblank >= MIN_ALTLOC_FRACTION * total) ? letter : null
    }

    private static boolean overlaps(Atoms candidate, List<Atoms> references) {
        if (references.isEmpty() || candidate.count == 0) {
            return false
        }
        Atoms ref = Atoms.join(references)
        if (ref.count == 0) {
            return false
        }
        ref.withKdTreeConditional()
        int within = 0
        int need = (int) Math.ceil(MIN_OVERLAP_FRACTION * candidate.count)
        for (Atom a : candidate) {
            if (ref.areWithinDistance(a, OVERLAP_DISTANCE)) {
                within++
                if (within >= need) {
                    return true
                }
            }
        }
        return false
    }

    private static String chainId(Chain ch) {
        String id = ch.getId()
        if (id == null || id.isEmpty()) {
            id = ch.getName()
        }
        return id ?: ""
    }

}
