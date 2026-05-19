package cz.siret.prank.program.routines.predict.output.descriptors;

import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;

/**
 * Number of distinct residues touching the pocket. Reuses
 * {@code Pocket.getResidues()} which lazily derives the list from
 * {@code surfaceAtoms.distinctGroupsSorted}.
 */
public final class NumResiduesDescriptor extends AbstractScalarPocketDescriptor {

    @Override public String name() { return "num_residues"; }
    @Override protected ColumnType scalarType() { return ColumnType.INT; }
    @Override public boolean needsGrid() { return false; }

    @Override
    protected double computeScalar(PocketGridContext ctx) {
        return ctx.pocket().getResidues().size();
    }

}
