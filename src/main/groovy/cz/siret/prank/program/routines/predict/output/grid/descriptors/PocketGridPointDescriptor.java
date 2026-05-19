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

    /** One value per columnNames() entry, same order. */
    double[] compute(PocketGridPointContext ctx);

}
