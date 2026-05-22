package cz.siret.prank.program.routines.predict.output.descriptors;

import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;

import java.util.Collections;
import java.util.List;

/**
 * Sugar adapter for single-column {@link PocketDescriptor} implementations.
 *
 * <p>Most existing descriptors are scalar (one value per pocket); having them
 * implement the multi-column interface directly is noise. Subclasses just
 * override {@link #name}, {@link #scalarType}, and {@link #computeScalar}.
 *
 * <p>The single sub-column-name is the empty string — the header builder in
 * {@link cz.siret.prank.program.routines.predict.output.PocketDescriptorsRows}
 * special-cases the scalar branch (size 1) by emitting {@link #name} alone,
 * so the empty entry never reaches the output schema.
 *
 * <p>Parallel to
 * {@link cz.siret.prank.program.routines.predict.output.grid.descriptors.PocketGridPointDescriptor}'s
 * "single-column impls use a one-element list" convention (no shared adapter
 * exists for that interface yet because both registered descriptors are multi-column).
 */
public abstract class AbstractScalarPocketDescriptor implements PocketDescriptor {

    private static final List<String> SCALAR_NAMES = Collections.singletonList("");

    @Override
    public final List<String> columnNames() {
        return SCALAR_NAMES;
    }

    @Override
    public final List<ColumnType> columnTypes() {
        return Collections.singletonList(scalarType());
    }

    @Override
    public final double[] compute(PocketGridContext ctx) {
        return new double[] { computeScalar(ctx) };
    }

    protected abstract ColumnType scalarType();

    protected abstract double computeScalar(PocketGridContext ctx);

}
