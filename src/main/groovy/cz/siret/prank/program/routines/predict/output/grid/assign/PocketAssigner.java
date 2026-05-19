package cz.siret.prank.program.routines.predict.output.grid.assign;

import cz.siret.prank.geom.Atoms;
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid;

import java.util.BitSet;

/**
 * Strategy interface for the "points-within-cutoff" range query that produces a
 * pocket's raw shell. Given a set of input points (in practice
 * {@code Pocket.sasPoints}) and the full kept-grid, returns the set of
 * grid-point indices that fall within {@code assignCutoff} of any input point.
 *
 * <p>Decoupled from {@link cz.siret.prank.domain.Pocket} on purpose — the
 * caller is responsible for selecting which atoms drive the query, and the
 * same range query can be reused for non-pocket inputs (e.g., a future
 * "grid around ligand atoms" feature) without widening this interface.
 *
 * <p>Two shipping strategies:
 * <ul>
 *   <li>{@code kdtree} — build a KdTree on the grid, range-query around each input point.
 *       Best when the grid is dense (small spacing) or when many calls share the same
 *       neighborhood and the up-front KdTree build amortizes.</li>
 *   <li>{@code voxel_hash} — walk the small box of lattice cells around each input point
 *       and look up via {@code grid.getLatticeIndex()}. Best when the grid is coarse and
 *       the cells-per-cutoff count is small.</li>
 * </ul>
 *
 * <p>Implementations must be stateless after {@link #initialize}. The builder
 * calls {@code initialize} once per protein, then {@link #computeRawShell}
 * once per pocket (sequentially today — outer per-protein parallelism is the
 * project's concurrency unit).
 */
public interface PocketAssigner {

    /** Stable name; matches the user-facing {@code -pocket_grid_assigner} param value. */
    String name();

    /**
     * Optional one-time per-protein setup. Default no-op; KdTreeAssigner uses it to
     * trigger {@code grid.getAllPoints().withKdTree()} so per-call queries are fast.
     */
    default void initialize(PocketGrid grid) {}

    /**
     * @param inputPoints  the points the cutoff is measured FROM. Caller must guarantee
     *                     non-empty (empty inputs should be filtered before dispatch).
     * @param grid         the populated pocket grid (allPoints + latticeIndex available).
     * @param assignCutoff distance threshold (Å).
     * @return indices in {@code grid.getAllPoints()} within {@code assignCutoff} of
     *         any input point. Implementations must not mutate the grid.
     */
    BitSet computeRawShell(Atoms inputPoints, PocketGrid grid, double assignCutoff);

}
