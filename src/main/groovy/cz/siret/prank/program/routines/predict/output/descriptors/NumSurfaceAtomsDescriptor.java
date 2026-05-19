package cz.siret.prank.program.routines.predict.output.descriptors;

import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;

/**
 * Number of pocket surface atoms — the size of {@code pocket.getSurfaceAtoms()}.
 */
public final class NumSurfaceAtomsDescriptor implements PocketDescriptor {

    @Override public String name() { return "num_surface_atoms"; }
    @Override public ColumnType columnType() { return ColumnType.INT; }
    @Override public boolean needsGrid() { return false; }

    @Override
    public double compute(PocketGridContext ctx) {
        return ctx.pocket().getSurfaceAtoms().getCount();
    }

}
