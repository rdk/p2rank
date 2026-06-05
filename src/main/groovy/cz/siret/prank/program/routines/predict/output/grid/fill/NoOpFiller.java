package cz.siret.prank.program.routines.predict.output.grid.fill;

import cz.siret.prank.program.routines.predict.output.grid.PocketGrid;

import java.util.BitSet;

/**
 * Identity filler — returns a clone of the raw shell. Selected via
 * {@code -pocket_grid_fill none}.
 *
 * <p>Clones rather than aliasing because the {@link PocketShapeFiller}
 * contract gives the caller ownership of the returned BitSet (the builder
 * stores it as the per-pocket index set, which it may later mutate).
 */
public final class NoOpFiller implements PocketShapeFiller {

    @Override
    public BitSet fill(BitSet rawShell, PocketGrid grid, FillKnobs knobs) {
        return (BitSet) rawShell.clone();
    }

}
