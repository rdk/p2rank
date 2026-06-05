package cz.siret.prank.program.routines.predict.output.grid.fill;

import cz.siret.prank.program.routines.predict.output.grid.PocketGrid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(MorphologicalCloser.class);

    @Override
    public BitSet fill(BitSet rawShell, PocketGrid grid, FillKnobs knobs) {
        FillKnobs.Morph mk = (FillKnobs.Morph) knobs;
        int minNeighbors = mk.minNeighbors();
        int maxIters = mk.maxIters();
        if (rawShell.isEmpty()) return (BitSet) rawShell.clone();

        BitSet filled = (BitSet) rawShell.clone();
        BitSet newlyAdded = (BitSet) rawShell.clone();
        // Scratch BitSets reused across iterations — cleared at the start of each iter
        // and swapped with newlyAdded at the end so the loop allocates zero BitSets
        // per iteration (vs the previous one-candidates + one-promoted per iter).
        BitSet candidates = new BitSet();
        BitSet promoted = new BitSet();
        int[] buf = new int[26];  // reused buffer for neighbor lookups, zero per-call alloc

        int iter = 0;
        boolean converged = false;
        for (; iter < maxIters; iter++) {
            // Step 1: collect candidates — unfilled cells adjacent to anything just promoted.
            // This is a single dilation pass of the frontier (newlyAdded) against the full
            // filled set; the shared primitive clears `candidates` and reuses `buf`.
            if (!LatticeMorphology.dilateOnce(newlyAdded, filled, grid, candidates, buf)) {
                converged = true; break;
            }

            // Step 2: promote candidates whose filled-neighbor count meets threshold.
            promoted.clear();
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

            if (promoted.isEmpty()) { converged = true; break; }
            filled.or(promoted);
            // Swap: next iter reads from the just-promoted bits, prev newlyAdded becomes
            // the next scratch (will be cleared at the top of the loop).
            BitSet tmp = newlyAdded;
            newlyAdded = promoted;
            promoted = tmp;
        }

        // maxIters=0 is a valid "disable fill" config — don't surface it as under-convergence.
        if (!converged && maxIters > 0) {
            log.warn("MorphologicalCloser: hit maxIters={} without converging " +
                    "(filled cells: {}, last iter promoted some). " +
                    "Raise -pocket_grid_fill_max_iters or accept under-converged fill.",
                    maxIters, filled.cardinality());
        }
        return filled;
    }

}
