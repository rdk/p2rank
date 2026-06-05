package cz.siret.prank.program.routines.predict.output.grid.fill;

import cz.siret.prank.program.routines.predict.output.grid.PocketGrid;

import java.util.BitSet;

/**
 * PROTOTYPE. True binary morphological closing on the lattice: dilate the raw
 * shell by {@code radius} layers (26-connectivity), then erode by the same
 * {@code radius}. Closing fills holes and concavities up to width {@code 2*radius}
 * while restoring the outer boundary, so it does NOT balloon the pocket outward
 * the way {@link MorphologicalCloser} (dilation with no erosion) does.
 *
 * <p>Why this fixes the over-overlap: each pocket's SAS shell sits in one
 * contiguous lattice envelope shared with neighbouring pockets.
 * {@code MorphologicalCloser} keeps dilating across that envelope (a flat front
 * presents 9 filled neighbours, above the default {@code min_neighbors=4}
 * threshold, so it never stops) until {@code max_iters}, engulfing neighbours.
 * Closing instead dilates then erodes: dilation that reached into the open
 * inter-pocket region is peeled back by erosion, so two pockets only merge if
 * the gap between their shells is a fully enclosed cavity narrower than
 * {@code 2*radius}. Pick a small radius (1-3).
 *
 * <p>Knob mapping (reusing the existing fill knobs for the prototype):
 * <ul>
 *   <li>{@code maxIters} -> closing radius (dilate N, erode N). Use small values.</li>
 *   <li>{@code minNeighbors} -> ignored (dilation/erosion use 26-connectivity).</li>
 * </ul>
 *
 * <p>Erosion treats a missing lattice neighbour (envelope edge, no grid point)
 * as "not empty" -- it only erodes a cell that has an actually-present empty
 * neighbour. This keeps erosion from peeling the legitimate outer surface at the
 * envelope boundary while still cancelling dilation that leaked toward another
 * pocket through existing (open) cells.
 */
public final class ErodeDilateCloser implements PocketShapeFiller {

    @Override
    public BitSet fill(BitSet rawShell, PocketGrid grid, FillKnobs knobs) {
        FillKnobs.Closing ck = (FillKnobs.Closing) knobs;
        BitSet filled = (BitSet) rawShell.clone();
        // Dilate dilateRadius layers, then erode erodeRadius. Symmetric (erode == dilate)
        // is boundary-preserving true closing; asymmetric (erode < dilate) nets
        // (dilate-erode) layers of OUTWARD growth — a bounded version of morph's bleed.
        int dilateCount = ck.dilateRadius();
        int erodeCount = ck.erodeRadius();
        if (filled.isEmpty() || dilateCount <= 0) return filled;

        int[] buf = new int[26];

        // --- dilate `dilateCount` times: add every existing empty neighbour of a filled cell ---
        for (int it = 0; it < dilateCount; it++) {
            BitSet add = new BitSet();
            for (int i = filled.nextSetBit(0); i >= 0; i = filled.nextSetBit(i + 1)) {
                int nn = grid.neighborsInto(i, buf);
                for (int k = 0; k < nn; k++) {
                    int nbr = buf[k];
                    if (!filled.get(nbr)) add.set(nbr);
                }
            }
            if (add.isEmpty()) break;   // converged: nothing left to dilate into
            filled.or(add);
        }

        // --- erode `erodeCount` times: drop any cell with an existing empty neighbour ---
        for (int it = 0; it < erodeCount; it++) {
            BitSet remove = new BitSet();
            for (int i = filled.nextSetBit(0); i >= 0; i = filled.nextSetBit(i + 1)) {
                int nn = grid.neighborsInto(i, buf);
                for (int k = 0; k < nn; k++) {
                    if (!filled.get(buf[k])) { remove.set(i); break; }
                }
            }
            if (remove.isEmpty()) break;
            filled.andNot(remove);
        }

        // Closing is extensive: the result always contains the original raw shell.
        filled.or(rawShell);
        return filled;
    }

}
