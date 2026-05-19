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
}
