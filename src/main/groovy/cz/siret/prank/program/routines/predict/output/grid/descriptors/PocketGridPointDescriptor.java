package cz.siret.prank.program.routines.predict.output.grid.descriptors;

import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;

import java.util.List;

/**
 * Pluggable per-grid-point descriptor — adds extra columns to the pocket-grid
 * export, one value per descriptor column per (point, pocket) row.
 *
 * <p>Parallel to {@link cz.siret.prank.program.routines.predict.output.descriptors.PocketDescriptor}
 * but the unit is a single grid point in the context of one pocket-row
 * (the same point can appear in multiple rows; descriptors that don't depend
 * on the pocket compute the same value repeatedly — caching across rows is
 * the descriptor's responsibility if the cost matters).
 *
 * <p>Implementations should be stateless and thread-safe.
 *
 * <p>Every grid-point descriptor needs the grid by definition — the grid is the
 * substrate that defines what "a grid point" is. The per-pocket descriptor
 * interface has an optional {@code needsGrid()} method for cheap descriptors
 * that only read pocket fields; this interface deliberately omits it. The
 * orchestrator always builds the grid when any grid-point descriptor is
 * selected.
 */
public interface PocketGridPointDescriptor {

    /** CLI token; prefix for output columns when this descriptor is multi-column. */
    String name();

    /**
     * Column names this descriptor produces.
     *   - Scalar (size 1): entry IGNORED at output; column header is exactly name().
     *   - Multi-column (size > 1): each header becomes "{name()}.{columnNames().get(i)}".
     */
    List<String> columnNames();

    /** Parallel to columnNames(). */
    List<ColumnType> columnTypes();

    /**
     * When {@code true}, the runner caches the result per point and reuses it for every
     * pocket the point belongs to. Implementations returning {@code true} MUST NOT read
     * {@code ctx.pocket()} or {@code ctx.pocketRank()} — violating this silently reuses
     * the first pocket's result as the answer for the others.
     */
    default boolean isPocketAgnostic() { return false; }

    /**
     * Write {@code columnNames().size()} doubles into {@code out[offset .. offset+N)}.
     * Direct-write SPI rather than {@code return double[]} so the runner can hand
     * descriptors the destination cell of the output row directly — saves a per-call
     * allocation and a copy loop on the hot path.
     */
    void compute(PocketGridPointContext ctx, double[] out, int offset);

}
