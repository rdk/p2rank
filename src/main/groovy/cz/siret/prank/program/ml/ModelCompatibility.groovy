package cz.siret.prank.program.ml

import cz.siret.prank.program.PrankException
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.annotation.Nullable

/**
 * Validates that a loaded model's stored feature header matches the feature header
 * produced by the current configuration, so that running a model with a non-matching
 * config does not silently yield incorrect predictions.
 *
 * See features.txt written by {@link Model#saveToDirectoryV3(java.lang.String)}.
 */
@Slf4j
@CompileStatic
class ModelCompatibility {

    /** Result of comparing a model's stored feature header with the current one. */
    static class Result {
        boolean match
        int storedSize
        int currentSize
        List<String> missingInCurrent = []     // present in model, absent from current config
        List<String> unexpectedInCurrent = []  // present in current config, absent from model
        @Nullable Integer firstDivergenceIndex // first index where the ordered headers differ, null if identical
        boolean sameSetWrongOrder              // same set of features, different order
    }

    /**
     * Order-sensitive comparison of a stored feature header against the current one.
     */
    static Result compare(List<String> stored, List<String> current) {
        Result r = new Result()
        r.storedSize = stored.size()
        r.currentSize = current.size()
        r.match = (stored == current)
        if (r.match) {
            return r
        }

        Set<String> storedSet = new LinkedHashSet<>(stored)
        Set<String> currentSet = new LinkedHashSet<>(current)
        r.missingInCurrent = stored.findAll { !currentSet.contains(it) }.unique()
        r.unexpectedInCurrent = current.findAll { !storedSet.contains(it) }.unique()
        r.sameSetWrongOrder = r.missingInCurrent.isEmpty() && r.unexpectedInCurrent.isEmpty()

        Integer fdi = null
        int min = Math.min(stored.size(), current.size())
        for (int i = 0; i != min; i++) {
            if (stored[i] != current[i]) {
                fdi = i
                break
            }
        }
        if (fdi == null && stored.size() != current.size()) {
            fdi = min
        }
        r.firstDivergenceIndex = fdi

        return r
    }

    /**
     * Validate that a loaded model's stored feature header matches the header produced by the current config.
     * No-op when the model carries no stored header (legacy v1/v2 models or old v3 dirs without features.txt).
     *
     * @param failOnMismatch true -> throw PrankException on mismatch; false -> log a warning and continue
     */
    static void check(Model model, List<String> currentHeader, boolean failOnMismatch) {
        List<String> stored = model.storedFeatureHeader
        if (stored == null) {
            log.debug "Model '{}' has no stored feature header (legacy or pre-header model); skipping compatibility check", model.label
            return
        }

        Result r = compare(stored, currentHeader)
        if (r.match) {
            log.debug "Model '{}' feature header matches current configuration ({} features)", model.label, r.currentSize
            return
        }

        String msg = formatMessage(model, r)
        if (failOnMismatch) {
            throw new PrankException(msg)
        } else {
            log.warn msg
        }
    }

    private static String formatMessage(Model model, Result r) {
        List<String> lines = []
        String src = model.sourceDir != null ? " (loaded from ${model.sourceDir})" : ""
        lines.add("Model/config feature mismatch for model '${model.label}'${src}.".toString())
        lines.add("  the model was trained with ${r.storedSize} features; the current configuration produces ${r.currentSize}.".toString())
        if (r.sameSetWrongOrder) {
            String at = r.firstDivergenceIndex != null ? " (first divergence at position ${r.firstDivergenceIndex})" : ""
            lines.add("  the feature set is the same but the ORDER differs${at}.".toString())
        } else {
            if (!r.unexpectedInCurrent.isEmpty()) {
                lines.add("  unexpected (in current config, not in model): ${r.unexpectedInCurrent}".toString())
            }
            if (!r.missingInCurrent.isEmpty()) {
                lines.add("  missing    (in model, not in current config): ${r.missingInCurrent}".toString())
            }
        }
        lines.add("Predictions would be silently incorrect. Use the configuration that matches this model, " +
                "or set -fail_on_model_feature_mismatch 0 to override (results may be wrong).")
        return lines.join("\n")
    }

}
