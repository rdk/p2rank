package cz.siret.prank.program.routines.predict.output.descriptors;

import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;

/**
 * Number of pocket surface atoms — the size of {@code pocket.getSurfaceAtoms()}.
 */
public final class NumSurfaceAtomsDescriptor extends AbstractScalarPocketDescriptor {

    @Override public String name() { return "num_surface_atoms"; }
    @Override protected ColumnType scalarType() { return ColumnType.INT; }
    @Override public boolean needsGrid() { return false; }

    @Override
    protected double computeScalar(PocketGridContext ctx) {
        return ctx.pocket().getSurfaceAtoms().getCount();
    }

}
