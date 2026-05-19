package cz.siret.prank.program.routines.predict.output.descriptors;

import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;

/**
 * Total number of grid points assigned to the pocket — the cardinality of
 * the pocket's {@code BitSet} after the shape fill.
 *
 * <p>Useful as a raw size complement to {@code volume}: scales linearly with
 * cell count whereas volume scales with {@code count * spacing³}.
 */
public final class NumGridPointsDescriptor extends AbstractScalarPocketDescriptor {

    @Override public String name() { return "num_grid_points"; }
    @Override protected ColumnType scalarType() { return ColumnType.INT; }

    @Override
    protected double computeScalar(PocketGridContext ctx) {
        return ctx.gridPointIndices().cardinality();
    }

}
