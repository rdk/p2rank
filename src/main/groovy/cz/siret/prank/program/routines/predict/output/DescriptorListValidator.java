package cz.siret.prank.program.routines.predict.output;

import cz.siret.prank.program.PrankException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates a name-list param (e.g. {@code -pocket_descriptors},
 * {@code -pocket_grid_point_descriptors}) against the names registered in a
 * descriptor registry. Rejects:
 *
 * <ul>
 *   <li>null or blank entries — a malformed config file should fail fast rather than
 *       slip through and hit {@code Registry.get('')} with a less useful error.</li>
 *   <li>unknown names — names not registered in the registry.</li>
 *   <li>duplicates — would produce duplicate output columns and break Parquet's
 *       schema builder.</li>
 * </ul>
 *
 * <p>A {@code null} list is treated as "no entries selected" and accepted without
 * change — matches the existing Params convention where a missing list means
 * "no extra columns".
 */
public final class DescriptorListValidator {

    private DescriptorListValidator() {}

    /**
     * @param names      values passed on the CLI / from a config file. A {@code null}
     *                   list is treated as empty and accepted.
     * @param known      the registry's known-names set (from {@code Registry.knownNames()}).
     * @param paramName  the Params property name, used to format error messages
     *                   (the leading {@code -} is added by the validator).
     */
    public static void validate(List<String> names, Set<String> known, String paramName)
            throws PrankException {
        if (names == null) return;
        // LinkedHashSet so any future "all duplicates" debug output preserves the
        // user-supplied order (HashSet would scramble it).
        Set<String> seen = new LinkedHashSet<>();
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) {
                throw new PrankException(
                        "-" + paramName + " contains an empty/null entry. Known: " + known);
            }
            if (!known.contains(name)) {
                throw new PrankException(
                        "Unknown name in -" + paramName + ": '" + name + "'. Known: " + known);
            }
            if (!seen.add(name)) {
                throw new PrankException(
                        "-" + paramName + " contains duplicate name '" + name + "'. " +
                        "Each descriptor may be listed at most once.");
            }
        }
    }

}
