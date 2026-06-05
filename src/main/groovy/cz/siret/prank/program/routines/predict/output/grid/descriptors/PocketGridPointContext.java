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
 * {@code -pocket_grid_include_unassigned} is set, and only in the tabular export).
 * {@code pocket} is non-null iff {@code pocketRank > 0}; descriptors that read
 * {@code pocket()} must therefore tolerate null (or declare themselves
 * pocket-agnostic, which is how all current descriptors avoid the issue).
 *
 * <p>Mutable on purpose: the runner allocates ONE instance and resets it per row
 * via {@link #reset}. Descriptors must not retain references across calls — the
 * fields will change on the next iteration. Pooling avoids ~50 k per-protein
 * record allocations in the hot loop.
 */
public final class PocketGridPointContext {

    private int pointIndex;
    private Atom point;
    private int pocketRank;
    @Nullable private Pocket pocket;
    private Protein protein;
    private PocketGrid grid;

    public PocketGridPointContext() {}

    /** Convenience constructor for tests + non-pooled use. */
    public PocketGridPointContext(int pointIndex, Atom point, int pocketRank,
                                  @Nullable Pocket pocket, Protein protein, PocketGrid grid) {
        reset(pointIndex, point, pocketRank, pocket, protein, grid);
    }

    /**
     * Mutate the context to point at the next row. Validates non-negative
     * {@code pointIndex} / {@code pocketRank} — limits the blast radius of an
     * int-arg swap at the call site.
     */
    public void reset(int pointIndex, Atom point, int pocketRank,
                      @Nullable Pocket pocket, Protein protein, PocketGrid grid) {
        if (pointIndex < 0) {
            throw new IllegalArgumentException("pointIndex must be >= 0 (got " + pointIndex + ")");
        }
        if (pocketRank < 0) {
            throw new IllegalArgumentException(
                    "pocketRank must be >= 0 (0 = unassigned; got " + pocketRank + ")");
        }
        this.pointIndex = pointIndex;
        this.point = point;
        this.pocketRank = pocketRank;
        this.pocket = pocket;
        this.protein = protein;
        this.grid = grid;
    }

    public int pointIndex() { return pointIndex; }
    public Atom point() { return point; }
    public int pocketRank() { return pocketRank; }
    @Nullable public Pocket pocket() { return pocket; }
    public Protein protein() { return protein; }
    public PocketGrid grid() { return grid; }
}
