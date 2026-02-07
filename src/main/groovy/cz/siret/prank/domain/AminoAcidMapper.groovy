package cz.siret.prank.domain

import cz.siret.prank.program.PrankException
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Maps non-canonical amino acid residue codes to standard 20 amino acids.
 *
 * Singleton initialized at startup based on aa_mapping parameter.
 * Thread-safe with lazy initialization for library API support.
 */
@Slf4j
@CompileStatic
class AminoAcidMapper {

    private static volatile AminoAcidMapper INSTANCE = null

    private final Map<String, String> mappings
    private final String mode

    private AminoAcidMapper(Map<String, String> mappings, String mode) {
        this.mappings = Collections.unmodifiableMap(mappings)
        this.mode = mode
    }

    static AminoAcidMapper getInstance() {
        if (INSTANCE == null) {
            synchronized (AminoAcidMapper.class) {
                if (INSTANCE == null) {
                    // Auto-initialize with default for library users who don't call Main.initParams()
                    log.debug "AminoAcidMapper auto-initializing with 'minimal' mode"
                    initializeInternal("minimal")
                }
            }
        }
        return INSTANCE
    }

    /**
     * Initialize mapper based on mode parameter.
     * Thread-safe, can be called multiple times (last call wins).
     * @param mode "minimal", "pdbfixer", or path to custom CSV file
     */
    static synchronized void initialize(String mode) {
        initializeInternal(mode)
    }

    private static void initializeInternal(String mode) {
        String effectiveMode = (mode == null) ? "minimal" : mode
        Map<String, String> mappings = loadMappings(effectiveMode)
        INSTANCE = new AminoAcidMapper(mappings, effectiveMode)
        log.info "AA mapping mode: {} ({} mappings loaded)", effectiveMode, mappings.size()
    }

    /**
     * Reset instance to uninitialized state.
     * Intended for testing only - do not use in production code.
     */
    static synchronized void reset() {
        INSTANCE = null
    }

    /**
     * Get an unmodifiable view of current mappings (for debugging/diagnostics).
     */
    Map<String, String> getMappings() {
        return mappings  // Already unmodifiable from constructor
    }

    @Override
    String toString() {
        return "AminoAcidMapper[mode=$mode, mappings=${mappings.size()}]"
    }

    /**
     * Map residue code to standard amino acid code.
     * @param code 3-letter residue code (e.g., "LLP", "MSE", "ALA")
     * @return mapped code or original if no mapping exists
     */
    String map(String code) {
        if (code == null || code.isEmpty()) return code
        String upper = code.toUpperCase()
        return mappings.getOrDefault(upper, upper)
    }

    int getMappingCount() {
        return mappings.size()
    }

    String getMode() {
        return mode
    }

    private static final Set<String> BUILT_IN_MODES = ["minimal", "pdbfixer"] as Set

    private static Map<String, String> loadMappings(String mode) {
        if (mode == null || mode == "minimal") {
            return loadMinimalMappings()
        } else if (mode == "pdbfixer") {
            return loadFromResource("/mappings/aa-mapping-pdbfixer.csv")
        } else {
            // Anything else is treated as a file path
            return loadFromFile(mode)
        }
    }

    /**
     * Check if a mode string refers to a built-in mode.
     * Useful for validation or documentation purposes.
     */
    static boolean isBuiltInMode(String mode) {
        return mode == null || BUILT_IN_MODES.contains(mode)
    }

    private static Map<String, String> loadMinimalMappings() {
        // Preserve exact existing behavior from PdbUtils.correctResidueCode()
        return [
            "MSE": "MET",  // Selenomethionine → Methionine
            "MEN": "ASN"   // N-Methyl Asparagine → Asparagine
        ]
    }

    private static Map<String, String> loadFromResource(String resourcePath) {
        try {
            String content = Futils.readResource(resourcePath)
            return parseCsv(content, resourcePath)
        } catch (Exception e) {
            throw new PrankException("Failed to load AA mapping from resource: $resourcePath", e)
        }
    }

    private static Map<String, String> loadFromFile(String filePath) {
        File file = new File(filePath)
        if (!file.exists()) {
            throw new PrankException("AA mapping file not found: $filePath")
        }
        if (!file.canRead()) {
            throw new PrankException("AA mapping file not readable: $filePath")
        }
        try {
            String content = file.text
            return parseCsv(content, filePath)
        } catch (PrankException e) {
            throw e
        } catch (Exception e) {
            throw new PrankException("Failed to load AA mapping from file: $filePath", e)
        }
    }

    private static Map<String, String> parseCsv(String content, String source) {
        Map<String, String> result = new LinkedHashMap<>()
        int lineNum = 0

        for (String line : content.readLines()) {
            lineNum++
            line = line.trim()

            // Skip comments and empty lines
            if (line.isEmpty() || line.startsWith("#")) {
                continue
            }

            String[] parts = line.split(",")
            if (parts.length != 2) {
                log.warn "AA mapping {}: line {}: invalid format (expected 2 columns): {}",
                    source, lineNum, line
                continue
            }

            String from = parts[0].trim().toUpperCase()
            String to = parts[1].trim().toUpperCase()

            // Validate codes (1-4 alphanumeric chars)
            if (!from.matches("[A-Z0-9]{1,4}")) {
                log.warn "AA mapping {}: line {}: invalid source code: {}", source, lineNum, from
                continue
            }
            if (!to.matches("[A-Z0-9]{1,4}")) {
                log.warn "AA mapping {}: line {}: invalid target code: {}", source, lineNum, to
                continue
            }

            // Skip self-mappings (no-ops)
            if (from == to) {
                log.debug "AA mapping {}: line {}: skipping self-mapping '{}'", source, lineNum, from
                continue
            }

            // Warn on non-standard target (not one of 20 standard AAs)
            if (AA.forCode(to) == null) {
                log.warn "AA mapping {}: line {}: target '{}' is not a standard amino acid",
                    source, lineNum, to
            }

            // Warn on duplicate (keep first)
            if (result.containsKey(from)) {
                log.warn "AA mapping {}: line {}: duplicate mapping for '{}' (keeping first)",
                    source, lineNum, from
                continue
            }

            result.put(from, to)
        }

        if (result.isEmpty()) {
            log.warn "AA mapping {}: no valid mappings found", source
        }

        return result
    }
}
