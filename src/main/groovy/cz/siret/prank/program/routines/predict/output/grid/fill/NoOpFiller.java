package cz.siret.prank.program.routines.predict.output.grid.fill;

import cz.siret.prank.program.routines.predict.output.grid.PocketGrid;

import java.util.BitSet;

/**
 * Identity filler — returns the raw shell unchanged. Selected via
 * {@code -pocket_grid_fill none}.
 */
public final class NoOpFiller implements PocketShapeFiller {

    @Override
    public BitSet fill(BitSet rawShell, PocketGrid grid, int minNeighbors, int maxIters) {
        return rawShell;
    }

}
