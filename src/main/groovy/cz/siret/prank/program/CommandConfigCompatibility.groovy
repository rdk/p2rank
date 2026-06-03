package cz.siret.prank.program

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.annotation.Nullable

/**
 * Validates that a command is run with a config of the matching purpose, so that a prediction
 * config is not used with a rescoring command (or vice versa).
 *
 * Configs declare their purpose via the {@code config_purpose} parameter ("prediction" / "rescoring",
 * empty = unrestricted). See issue #73 (e.g. {@code prank rescore -c alphafold}).
 *
 * Orthogonal to {@link cz.siret.prank.program.ml.ModelCompatibility}, which compares feature headers.
 */
@Slf4j
@CompileStatic
class CommandConfigCompatibility {

    /** Commands that require a config of a particular purpose. Commands not listed are unrestricted. */
    static final Map<String, String> COMMAND_PURPOSE = [
            'predict'         : 'prediction',
            'eval-predict'    : 'prediction',
            'rescore'         : 'rescoring',
            'fpocket-rescore' : 'rescoring',
            'eval-rescore'    : 'rescoring',
    ].asImmutable() as Map<String, String>

    static final Set<String> VALID_PURPOSES = (['', 'prediction', 'rescoring'] as Set).asImmutable()

    /**
     * @param command        current command (e.g. 'rescore')
     * @param configPurpose  value of params.config_purpose ('' = unrestricted)
     * @param configName     the -c value, for the error message (or null)
     * @param failOnWrong    true = throw PrankException on mismatch; false = log a warning and continue
     */
    static void check(String command, String configPurpose, @Nullable String configName, boolean failOnWrong) {
        String purpose = configPurpose ?: ''
        if (!VALID_PURPOSES.contains(purpose)) {
            throw new PrankException("Invalid config_purpose '${purpose}'. Allowed values: prediction, rescoring (or empty).")
        }

        String expected = COMMAND_PURPOSE[command]
        if (expected == null || purpose.isEmpty()) {
            return // unmapped command or unrestricted config -> nothing to check
        }

        if (purpose != expected) {
            String msg = formatMessage(command, purpose, expected, configName)
            if (failOnWrong) {
                throw new PrankException(msg)
            } else {
                log.warn msg
            }
        }
    }

    private static String formatMessage(String command, String purpose, String expected, @Nullable String configName) {
        String cfg = configName ? "Config '${configName}'" : "The active config"
        String alts = (expected == 'rescoring')
                ? "default (no -c), rescore_2024, or rescore_conservation"
                : "default (no -c), alphafold, conservation_hmm, or alphafold_conservation_hmm"
        return "${cfg} is intended for ${purpose} (config_purpose=${purpose}), " +
                "but command '${command}' requires a ${expected} config.\n" +
                "  Use a ${expected} config: ${alts}.\n" +
                "  Override with -fail_on_wrong_config 0 (results may be wrong)."
    }

}
