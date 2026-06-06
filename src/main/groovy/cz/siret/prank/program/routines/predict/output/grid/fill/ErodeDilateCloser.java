package cz.siret.prank.program.routines.predict.output.grid.fill;

import cz.siret.prank.program.routines.predict.output.grid.PocketGrid;

import java.util.BitSet;

/**
 * True binary morphological closing on the lattice: dilate the raw
 * shell by {@code dilateRadius} layers (26-connectivity), then erode by
 * {@code erodeRadius}. Closing fills holes and concavities up to width
 * {@code 2*radius} while restoring the outer boundary, so it does NOT balloon the
 * pocket outward the way {@link MorphologicalCloser} (dilation with no erosion) does.
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
 * <p>Knobs are the typed {@link FillKnobs.Closing} record:
 * <ul>
 *   <li>{@code dilateRadius} -> dilation passes = the closing radius. Use small values (1-3).</li>
 *   <li>{@code erodeRadius} -> erosion passes. Symmetric ({@code erodeRadius == dilateRadius})
 *       is boundary-preserving true closing; asymmetric ({@code erodeRadius < dilateRadius})
 *       nets {@code dilateRadius - erodeRadius} layers of OUTWARD growth.</li>
 * </ul>
 * For an explicit, self-documenting call prefer {@link #close(BitSet, PocketGrid, int, int)}.
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
        return close(rawShell, grid, ck.dilateRadius(), ck.erodeRadius());
    }

    /**
     * Morphological closing with independently chosen dilation and erosion depths.
     * Symmetric ({@code erodeCount == dilateCount}) is true closing: it fills holes and
     * concavities up to width {@code 2*dilateCount} while restoring the outer boundary.
     * Asymmetric with {@code erodeCount < dilateCount} leaves {@code dilateCount - erodeCount}
     * net layers of OUTWARD growth on top of the hole-closing -- a bounded version of the
     * {@code morph_closing} outward bleed. Use the asymmetric form with care.
     *
     * <p>{@code dilateCount <= 0} is a no-op that returns a clone of the raw shell: with no
     * dilation there is nothing for erosion to peel back, so any {@code erodeCount} is ignored
     * (eroding the bare raw shell is never wanted). Production always passes the symmetric form.
     */
    public BitSet close(BitSet rawShell, PocketGrid grid, int dilateCount, int erodeCount) {
        BitSet filled = (BitSet) rawShell.clone();
        if (filled.isEmpty() || dilateCount <= 0) return filled;

        int[] buf = new int[26];
        BitSet scratch = new BitSet();   // reused across every pass — no per-iteration allocation

        // --- dilate `dilateCount` times: add every existing empty neighbour of a filled cell ---
        for (int it = 0; it < dilateCount; it++) {
            if (!LatticeMorphology.dilateOnce(filled, filled, grid, scratch, buf)) break;  // converged
            filled.or(scratch);
        }

        // --- erode `erodeCount` times: drop any cell with an existing empty neighbour ---
        for (int it = 0; it < erodeCount; it++) {
            if (!LatticeMorphology.erodeOnce(filled, grid, scratch, buf)) break;
            filled.andNot(scratch);
        }

        // Closing is extensive: the result always contains the original raw shell.
        filled.or(rawShell);
        return filled;
    }

}
