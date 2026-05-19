package cz.siret.prank.program.routines.predict.output.grid.fill;

import cz.siret.prank.program.routines.predict.output.grid.PocketGrid;

import java.util.BitSet;

/**
 * Iterative morphological closing on the lattice. Promotes any candidate
 * lattice cell (already in {@code grid.getAllPoints()}) whose 26-neighborhood
 * contains at least {@code minNeighbors} cells already in the filled set.
 * Iterates until fixed point or {@code maxIters} reached.
 *
 * <p>Operates per-pocket — each pocket's raw shell is dilated independently;
 * multi-pocket overlap is a natural consequence of cells satisfying the
 * neighbor count from more than one pocket.
 *
 * <p>{@link BitSet} storage gives zero autoboxing on add/contains/iterate and
 * vectorized union/intersect via {@link BitSet#or}. Frontier optimization:
 * only check candidates adjacent to cells promoted in the previous iteration
 * — total work is O(|filled| × 26) rather than O(|filled|² × 26).
 */
public final class MorphologicalCloser implements PocketShapeFiller {

    @Override
    public BitSet fill(BitSet rawShell, PocketGrid grid, int minNeighbors, int maxIters) {
        if (rawShell.isEmpty()) return (BitSet) rawShell.clone();

        BitSet filled = (BitSet) rawShell.clone();
        BitSet newlyAdded = (BitSet) rawShell.clone();
        int[] buf = new int[26];  // reused buffer for neighbor lookups, zero per-call alloc

        for (int iter = 0; iter < maxIters; iter++) {
            // Step 1: collect candidates — unfilled cells adjacent to anything just promoted.
            BitSet candidates = new BitSet();
            for (int i = newlyAdded.nextSetBit(0); i >= 0; i = newlyAdded.nextSetBit(i + 1)) {
                int nn = grid.neighborsInto(i, buf);
                for (int k = 0; k < nn; k++) {
                    int nbr = buf[k];
                    if (!filled.get(nbr)) {
                        candidates.set(nbr);
                    }
                }
            }
            if (candidates.isEmpty()) break;

            // Step 2: promote candidates whose filled-neighbor count meets threshold.
            BitSet promoted = new BitSet();
            for (int c = candidates.nextSetBit(0); c >= 0; c = candidates.nextSetBit(c + 1)) {
                int nn = grid.neighborsInto(c, buf);
                int count = 0;
                for (int k = 0; k < nn; k++) {
                    if (filled.get(buf[k])) {
                        count++;
                        if (count >= minNeighbors) {
                            promoted.set(c);
                            break;
                        }
                    }
                }
            }

            if (promoted.isEmpty()) break;
            filled.or(promoted);
            newlyAdded = promoted;
        }

        return filled;
    }

}
