package cz.siret.prank.program.routines.predict.output.descriptors;

import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;

/**
 * Number of distinct residues touching the pocket. Reuses
 * {@code Pocket.getResidues()} which lazily derives the list from
 * {@code surfaceAtoms.distinctGroupsSorted}.
 */
public final class NumResiduesDescriptor implements PocketDescriptor {

    @Override public String name() { return "num_residues"; }
    @Override public ColumnType columnType() { return ColumnType.INT; }
    @Override public boolean needsGrid() { return false; }

    @Override
    public double compute(PocketGridContext ctx) {
        return ctx.pocket().getResidues().size();
    }

}
