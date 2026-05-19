package cz.siret.prank.program.routines.predict.output.descriptors;

import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;

/**
 * Pluggable per-pocket descriptor.
 *
 * <p>Implementations should be stateless and thread-safe (descriptors are
 * computed across pockets, potentially in parallel).
 *
 * <p>Numeric INT descriptors return their value as a {@code double} that
 * a writer can downcast to int; this matches the {@link cz.siret.prank.program.routines.predict.output.TableData}
 * convention (see {@link cz.siret.prank.program.routines.predict.output.PointExportData} for precedent).
 */
public interface PocketDescriptor {

    /** Stable name; matches a token in {@code -pocket_descriptors}. */
    String name();

    /** Determines the output column's type in the descriptors file. */
    ColumnType columnType();

    /**
     * @return descriptor value for {@code ctx.pocket()}.
     *
     * <p>INT descriptors return their value as a {@code double} that the writer
     * downcasts at output (matches the {@link cz.siret.prank.program.routines.predict.output.TableData}
     * convention). Implementations must guarantee the value fits in i32 — for
     * pocket-grid counts (cells, residues, atoms), that's many orders of
     * magnitude of headroom.
     */
    double compute(PocketGridContext ctx);

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
