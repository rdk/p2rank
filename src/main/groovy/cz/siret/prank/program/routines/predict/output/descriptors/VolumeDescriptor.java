package cz.siret.prank.program.routines.predict.output.descriptors;

import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;

/**
 * Pocket volume estimated by lattice-cell count:
 * {@code volume = |assigned grid points| × spacing³}.
 *
 * <p>Unit: Å³. Accuracy scales with {@code pocket_grid_spacing}.
 */
public final class VolumeDescriptor extends AbstractScalarPocketDescriptor {

    @Override public String name() { return "volume"; }
    @Override protected ColumnType scalarType() { return ColumnType.DOUBLE; }

    @Override
    protected double computeScalar(PocketGridContext ctx) {
        double s = ctx.grid().getSpacing();
        return ctx.gridPointIndices().cardinality() * s * s * s;
    }

}
