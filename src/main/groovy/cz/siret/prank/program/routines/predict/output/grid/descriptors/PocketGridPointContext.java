package cz.siret.prank.program.routines.predict.output.grid.descriptors;

import cz.siret.prank.domain.Pocket;
import cz.siret.prank.domain.Protein;
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid;
import org.biojava.nbio.structure.Atom;

import javax.annotation.Nullable;

/**
 * Per-(point, pocket-row) context passed to {@link PocketGridPointDescriptor#compute}.
 *
 * <p>{@code pointIndex} is the index into {@code grid.getAllPoints()}; {@code point}
 * is the convenience shortcut. {@code pocketRank} is 1-based; {@code 0} means the
 * row is for an unassigned grid point (only present when
 * {@code -pocket_grid_include_unassigned} is set). {@code pocket} is non-null iff
 * {@code pocketRank > 0}.
 */
public record PocketGridPointContext(
        int pointIndex,
        Atom point,
        int pocketRank,
        @Nullable Pocket pocket,
        Protein protein,
        PocketGrid grid) {

    // Compact validator — limits the blast radius of an int-arg swap. Doesn't catch
    // pointIndex ↔ pocketRank swapped when both happen to be non-negative, but does
    // catch the common cases (negative index or rank from a misuse).
    public PocketGridPointContext {
        if (pointIndex < 0) {
            throw new IllegalArgumentException("pointIndex must be >= 0 (got " + pointIndex + ")");
        }
        if (pocketRank < 0) {
            throw new IllegalArgumentException(
                    "pocketRank must be >= 0 (0 = unassigned; got " + pocketRank + ")");
        }
    }
}
