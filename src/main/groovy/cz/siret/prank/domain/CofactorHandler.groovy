package cz.siret.prank.domain

import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.Struct
import cz.siret.prank.program.PrankException
import cz.siret.prank.utils.Formatter
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.Structure

import static cz.siret.prank.domain.Dataset.LigandDefinition
import static cz.siret.prank.geom.Struct.getAuthorId

/**
 * Identifies and extracts cofactor atoms that should be included in the protein surface.
 *
 * Cofactor specifiers reuse {@link Dataset.LigandDefinition} - the same syntax used by the
 * dataset 'ligands' column. A specifier can be a bare residue name ("FAD") or a precise
 * identifier ("FAD[group_id:A_500]", "FAD[atom_id:12345]", "FAD[contact_res_ids:...]").
 *
 * Per-structure instance. The only mutable state is the {@code matchedGroups} identity set,
 * which is reset and repopulated by {@link #extractCofactorAtoms}. Re-running extraction (e.g.
 * via {@code Protein.transformedCopy}, which reuses the same {@code LoaderParams} on a
 * deep-copied structure) is safe and discards prior group references. Since each
 * {@code Protein.loadStructure()} owns its own handler, there is no concurrent writer.
 *
 * {@code LoaderParams.isCofactor(Group)} delegates to this class.
 */
@Slf4j
@CompileStatic
class CofactorHandler {

    /** Key for storing ExtractionResult in Protein.secondaryData */
    static final String EXTRACTION_RESULT_KEY = "cofactor.extractionResult"

    /** Parsed cofactor specifiers. Immutable. */
    final List<LigandDefinition> definitions

    /**
     * Groups in the loaded structure that matched at least one definition.
     * Populated during {@link #extractCofactorAtoms}; used by {@link #isCofactor} for O(1)
     * exclusion checks in {@code Ligands.isRelevantLigandGroup}.
     *
     * Identity-based (BioJava Group references), not name-based - this is what makes precise
     * specifiers like {@code FAD[group_id:A_500]} work correctly when the structure contains
     * multiple FADs and only one of them is a cofactor.
     */
    private final Set<Group> matchedGroups = Collections.newSetFromMap(new IdentityHashMap<>())

    /**
     * Result of cofactor extraction from a structure.
     * Returned by {@link #extractCofactorAtoms} to avoid double-scanning the structure.
     */
    @CompileStatic
    static class ExtractionResult {
        /** Cofactor heavy atoms ready to add to proteinAtoms. */
        final Atoms atoms
        /** Map of cofactor name → matched groups (for logging). */
        final Map<String, List<Group>> foundGroups
        /** Specifiers (by originalString) that matched zero groups. */
        final List<String> unmatchedSpecifiers

        ExtractionResult(Atoms atoms, Map<String, List<Group>> foundGroups, List<String> unmatchedSpecifiers) {
            this.atoms = atoms
            this.foundGroups = foundGroups
            this.unmatchedSpecifiers = unmatchedSpecifiers
        }
    }

    /**
     * Create handler from parsed cofactor definitions.
     *
     * @param definitions parsed specifiers (e.g. via {@link #parseAndValidate})
     */
    CofactorHandler(List<LigandDefinition> definitions) {
        this.definitions = (List<LigandDefinition>) ((definitions ?: []) as List<LigandDefinition>).asImmutable()
    }

    /** @return true if any cofactor specifiers are configured */
    boolean isEnabled() {
        return !definitions.isEmpty()
    }

    /**
     * Check if a group was matched as a cofactor during extraction.
     * Returns false until {@link #extractCofactorAtoms} has been called.
     */
    boolean isCofactor(Group group) {
        if (group == null) return false
        return matchedGroups.contains(group)
    }

    /**
     * Extract cofactor atoms and metadata from the structure in a single scan.
     *
     * For each HETATM group in the structure, ask each definition if it matches (via
     * {@code LigandDefinition.matchesGroup}). Matched groups are remembered for {@link
     * #isCofactor} and their heavy atoms collected for the surface.
     *
     * @param protein the Protein under construction. {@code matchesGroup} with
     *                {@code contact_res_ids} consults {@code protein.residues} and
     *                {@code protein.proteinAtoms}, both of which must already be set.
     */
    ExtractionResult extractCofactorAtoms(Protein protein) {
        // Reset state - extraction may be re-run on a deep-copied structure (Protein.transformedCopy
        // reuses the same LoaderParams/CofactorHandler). Stale Group references from a prior structure
        // would otherwise persist in the identity set.
        matchedGroups.clear()

        Map<String, List<Group>> foundGroups = new LinkedHashMap<>()
        Set<String> matchedSpecifiers = new LinkedHashSet<>()
        List<Atom> atoms = new ArrayList<>()

        // Use the same candidate set as the ligand loader (HETATM ∪ non-polymer-chain groups,
        // minus covalently-bound residue HETs and water). Aligning with Ligands.loadForProtein
        // ensures GDP/GTP/ATP/SHR-style groups - which BioJava classifies as NUCLEOTIDE/AMINOACID
        // rather than HETATM - are reachable as cofactors.
        List<Group> candidateGroups = Struct.getLigandGroups(protein)

        for (Group g : candidateGroups) {
            boolean groupIsCofactor = false
            for (LigandDefinition d : definitions) {
                if (d.matchesGroup(g, protein)) {
                    groupIsCofactor = true
                    matchedSpecifiers.add(d.originalString)
                }
            }
            if (groupIsCofactor) {
                matchedGroups.add(g)
                // PDBName is non-null here: LigandDefinition.matchesGroup requires
                // groupName == group.getPDBName(), and groupName from parseAndValidate
                // is always non-null.
                String name = g.PDBName.toUpperCase()
                foundGroups.computeIfAbsent(name, { new ArrayList<>() }).add(g)
                atoms.addAll(Atoms.allFromGroup(g).withoutHydrogens().list)
            }
        }

        // A specifier is "unmatched" if no group in the structure satisfied it.
        List<String> unmatched = definitions
                .collect { it.originalString }
                .findAll { !matchedSpecifiers.contains(it) }

        return new ExtractionResult(new Atoms(atoms), foundGroups, unmatched)
    }

    /**
     * Log summary of extraction result.
     *
     * Levels:
     * - INFO: per-structure summary of matched cofactors
     * - DEBUG: unmatched specifiers + available HETATM list (prevents log flooding on large datasets)
     * - DEBUG: per-instance details (chain, residue number, atom count)
     */
    void logResult(ExtractionResult result, String structureName, Structure structure) {
        if (!isEnabled()) return

        if (!result.unmatchedSpecifiers.isEmpty() && log.isDebugEnabled()) {
            List<String> availableHet = Struct.getHetGroups(structure)
                    .collect { it.PDBName }
                    .findAll { it }
                    .unique()
                    .sort()
            String availableStr = availableHet.size() > 10
                    ? availableHet.take(10).join(", ") + "..."
                    : availableHet.join(", ")
            log.debug "Structure {}: cofactor specifier(s) {} matched no groups. Available HETATM groups: [{}]",
                    structureName, result.unmatchedSpecifiers, availableStr
        }

        if (!result.foundGroups.isEmpty()) {
            List<String> parts = new ArrayList<>()
            for (String name : result.foundGroups.keySet()) {
                List<Group> groups = result.foundGroups.get(name)
                int atomCount = (int) groups.sum { Atoms.allFromGroup((Group) it).withoutHydrogens().count }
                int instanceCount = groups.size()

                for (Group g : groups) {
                    String chainId = getAuthorId(g.chain)
                    String resNum = g.residueNumber?.printFull() ?: "?"
                    int groupAtomCount = Atoms.allFromGroup(g).withoutHydrogens().count
                    log.debug "Including cofactor {} (chain {}, residue {}, {} heavy atoms)",
                            name, chainId, resNum, groupAtomCount
                }

                if (instanceCount > 1) {
                    parts.add("${name}: ${atomCount} atoms (${instanceCount} instances)".toString())
                } else {
                    parts.add("${name}: ${atomCount} atoms".toString())
                }
            }

            log.info "Structure {}: included {} cofactor type(s) as protein surface ({})",
                    structureName, result.foundGroups.size(), parts.join(', ')
        }
    }

    // ===== Post-Extraction Checks =====

    /**
     * Log WARN messages for cofactor groups whose center of mass is far from the protein
     * surface. Distant cofactor may be a crystallization artifact or a free cofactor in
     * solvent. Advisory only - the cofactor stays in the surface regardless.
     */
    void warnDistantCofactors(ExtractionResult result, Atoms proteinAtoms,
                              double maxDist, String structureName) {
        if (maxDist <= 0 || result.foundGroups.isEmpty() || proteinAtoms.empty) return

        for (Map.Entry<String, List<Group>> e : result.foundGroups.entrySet()) {
            String name = e.key
            for (Group g : e.value) {
                Atoms groupAtoms = Atoms.allFromGroup(g).withoutHydrogens()
                if (groupAtoms.empty) continue

                double dist = proteinAtoms.dist(groupAtoms.centerOfMass)
                if (dist > maxDist) {
                    String chainId = getAuthorId(g.chain)
                    String resNum = g.residueNumber?.printFull() ?: "?"
                    log.warn "Structure {}: cofactor {} (chain {}, residue {}) " +
                            "is {} Å from nearest protein atom (threshold: {} Å) - " +
                            "may be a crystallization artifact",
                            structureName, name, chainId, resNum, Formatter.format(dist, 1), maxDist
                }
            }
        }
    }

    /**
     * Detect cofactors that exist in the full structure but were lost during chain reduction.
     * Called only when {@code onlyChains} is non-null.
     *
     * This diagnostic matches by groupName only - running the full {@code
     * LigandDefinition.matchesGroup} on the unreduced structure would need an alternate
     * {@code Protein} context (residues/atoms of the full structure), which we don't build.
     * The name-only check can over-report when a precise specifier wouldn't have matched the
     * chain-excluded group anyway; an acceptable false positive for an advisory log line.
     */
    void warnChainExcludedCofactors(Structure fullStructure, ExtractionResult reducedResult,
                                    String structureName) {
        Set<String> wantedNames = (Set<String>) definitions
                .collect { it.groupName?.toUpperCase() }
                .findAll { it as boolean }
                .toSet()

        Set<String> namesInFull = new LinkedHashSet<>()
        for (Group g : Struct.getGroups(fullStructure)) {
            if (!Struct.isLigandCandidateNonWaterGroup(g)) continue
            String n = g.PDBName?.toUpperCase()
            if (n != null && wantedNames.contains(n)) namesInFull.add(n)
        }

        Set<String> namesMatchedInReduced = reducedResult.foundGroups.keySet()

        Set<String> excluded = new LinkedHashSet<>(namesInFull)
        excluded.removeAll(namesMatchedInReduced)

        if (!excluded.isEmpty()) {
            log.warn "Structure {}: cofactor(s) {} found in full structure but not in reduced " +
                    "structure - they may exist only on excluded chains",
                    structureName, excluded
        }
    }

    // ===== Static Utility Methods =====

    /**
     * Parse and validate cofactor specifiers from a list of raw strings.
     *
     * The list is re-joined with commas and re-split using a bracket-aware splitter
     * before parsing. This recovers from naive comma-split that may have happened
     * upstream (e.g., {@code Sutils.parseList} from the CLI, or {@code CHAIN_SPLITTER}
     * from a dataset column), so a specifier like
     * {@code FAD[contact_res_ids:A_D246,A_T259,A_E423]} that was over-split into
     * {@code ["FAD[contact_res_ids:A_D246", "A_T259", "A_E423]"]} is correctly
     * reassembled into a single specifier.
     *
     * Each specifier is parsed via {@link #parseOne}, which:
     * - case-normalizes the group name (so {@code "fad"} → {@code "FAD"} matches BioJava)
     * - wraps {@code PrankException} from {@code LigandDefinition.parse} with cofactor-
     *   specific context, so the error message identifies the offending specifier
     *   rather than mentioning "dataset file" (which may not be the source)
     *
     * Blank entries are silently dropped (e.g., trailing commas).
     *
     * @return parsed (and therefore validated) definitions; never null
     */
    static List<LigandDefinition> parseAndValidate(List<String> rawSpecifiers) {
        if (rawSpecifiers == null || rawSpecifiers.isEmpty()) return []
        String joined = rawSpecifiers
                .findAll { it != null && !((String) it).trim().isEmpty() }
                .collect { ((String) it).trim() }
                .join(",")
        return parseAndValidate(joined)
    }

    /**
     * Parse and validate cofactor specifiers from a single comma-separated string,
     * e.g. a raw dataset column value. Splits with bracket awareness so that commas
     * inside {@code [...]} are preserved.
     */
    static List<LigandDefinition> parseAndValidate(String rawCommaSeparated) {
        if (rawCommaSeparated == null || rawCommaSeparated.trim().isEmpty()) return []
        List<String> parts = Sutils.splitRespectInnerParentheses(
                rawCommaSeparated, ',' as char, '[' as char, ']' as char)
        List<LigandDefinition> out = new ArrayList<>(parts.size())
        for (String s : parts) {
            String trimmed = s?.trim()
            if (trimmed == null || trimmed.isEmpty()) continue
            out.add(parseOne(trimmed))
        }
        return out
    }

    /**
     * Parse a single specifier with case normalization and cofactor-specific error context.
     *
     * The group-name portion (everything before the first {@code [}) is upper-cased so
     * that a user-supplied {@code "fad"} matches BioJava's uppercase PDB names. The
     * bracketed specifier portion is preserved as-is (chain IDs and contact-residue codes
     * are case-significant for group_id/contact_res_ids matching).
     */
    private static LigandDefinition parseOne(String raw) {
        int bracket = raw.indexOf('[')
        String normalized = (bracket < 0)
                ? raw.trim().toUpperCase()
                : raw.substring(0, bracket).trim().toUpperCase() + raw.substring(bracket)
        try {
            return LigandDefinition.parse(normalized)
        } catch (PrankException e) {
            throw new PrankException("Invalid cofactor specifier '${raw}': ${e.message}", e)
        }
    }
}
