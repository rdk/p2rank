package cz.siret.prank.program.routines.predict.output.descriptors;

import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;

import java.util.List;

/**
 * Pluggable per-pocket descriptor.
 *
 * <p>Each descriptor produces a fixed-arity vector — 1 column for scalar
 * descriptors (extend {@link AbstractScalarPocketDescriptor}), N columns for
 * multi-column descriptors (e.g. principal moments of inertia: three eigenvalues
 * from a single decomposition).
 *
 * <p>Header convention (applied by
 * {@link cz.siret.prank.program.routines.predict.output.PocketDescriptorsRows}):
 * <ul>
 *   <li>Scalar (size 1): the sub-name entry is IGNORED at output; column header
 *       is exactly {@link #name()}.</li>
 *   <li>Multi-column (size &gt; 1): each header becomes
 *       {@code "{name()}.{columnNames().get(i)}"} — e.g.
 *       {@code principal_moments.lambda1}.</li>
 * </ul>
 *
 * <p>Implementations should be stateless and thread-safe (descriptors are
 * computed across pockets, potentially in parallel).
 *
 * <p>INT columns return their value as a {@code double} that the writer
 * downcasts at output time, matching the {@link cz.siret.prank.program.routines.predict.output.TableData}
 * convention. Implementations must guarantee the value fits in i32.
 *
 * <p>This interface is the sibling of
 * {@link cz.siret.prank.program.routines.predict.output.grid.descriptors.PocketGridPointDescriptor}.
 * The metadata methods (name, columnNames, columnTypes) match; the {@code compute}
 * signatures differ — this one returns a {@code double[]} (Npockets/protein ~tens,
 * allocation is not a hot path) while the grid-point variant uses a direct-write
 * {@code compute(ctx, out, offset)} to avoid per-row allocation in its 10⁴–10⁵
 * per-protein loop.
 */
public interface PocketDescriptor {

    /** Stable name; matches a token in {@code -pocket_descriptors} and prefixes multi-column output headers. */
    String name();

    /**
     * Column names this descriptor produces.
     * <ul>
     *   <li>Scalar (size 1): entry IGNORED at output; column header is exactly {@link #name()}.</li>
     *   <li>Multi-column (size &gt; 1): each header becomes {@code "{name()}.{columnNames().get(i)}"}.</li>
     * </ul>
     */
    List<String> columnNames();

    /** Parallel to {@link #columnNames()} — one entry per output column. */
    List<ColumnType> columnTypes();

    /** @return one value per {@link #columnNames()} entry, in the same order. */
    double[] compute(PocketGridContext ctx);

    /**
     * Does {@link #compute} read the pocket grid ({@code ctx.grid()} or
     * {@code ctx.gridPointIndices()})? Defaults to {@code true} (the safe answer
     * for a new descriptor that hasn't declared otherwise). When all selected
     * descriptors return {@code false}, {@code PocketGridOutputs} skips the
     * grid build entirely — saving a per-protein full-grid construction that
     * would otherwise be wasted.
     */
    default boolean needsGrid() { return true; }

}
