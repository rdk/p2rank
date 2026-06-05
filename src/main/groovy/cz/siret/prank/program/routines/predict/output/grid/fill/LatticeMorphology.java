package cz.siret.prank.program.routines.predict.output.grid.fill;

import cz.siret.prank.program.routines.predict.output.grid.PocketGrid;

import java.util.BitSet;

/**
 * Shared single-pass binary morphology primitives on the lattice (26-connectivity), used by the
 * pocket-shape fillers. Both write into a caller-supplied scratch BitSet and use a caller-supplied
 * {@code int[26]} neighbour buffer, so a filler running these in a loop allocates nothing per pass.
 */
final class LatticeMorphology {

    private LatticeMorphology() {}

    /**
     * One dilation pass: into {@code out} (cleared first), collect every lattice neighbour of a
     * {@code source} cell that is not already in {@code filled}. {@code source} and {@code filled}
     * may be the same BitSet (full dilation) or differ (e.g. frontier dilation against the full
     * filled set). {@code buf} is a reusable 26-length scratch array.
     *
     * @return true if {@code out} is non-empty (something would be added)
     */
    static boolean dilateOnce(BitSet source, BitSet filled, PocketGrid grid, BitSet out, int[] buf) {
        out.clear();
        for (int i = source.nextSetBit(0); i >= 0; i = source.nextSetBit(i + 1)) {
            int nn = grid.neighborsInto(i, buf);
            for (int k = 0; k < nn; k++) {
                int nbr = buf[k];
                if (!filled.get(nbr)) out.set(nbr);
            }
        }
        return !out.isEmpty();
    }

    /**
     * One erosion pass: into {@code out} (cleared first), collect every {@code filled} cell that
     * has at least one present-but-empty lattice neighbour. A missing lattice neighbour (envelope
     * edge, no grid point) is treated as "not empty", so the legitimate outer surface at the
     * envelope boundary is not peeled. {@code buf} is a reusable 26-length scratch array.
     *
     * @return true if {@code out} is non-empty (something would be removed)
     */
    static boolean erodeOnce(BitSet filled, PocketGrid grid, BitSet out, int[] buf) {
        out.clear();
        for (int i = filled.nextSetBit(0); i >= 0; i = filled.nextSetBit(i + 1)) {
            int nn = grid.neighborsInto(i, buf);
            for (int k = 0; k < nn; k++) {
                if (!filled.get(buf[k])) { out.set(i); break; }
            }
        }
        return !out.isEmpty();
    }
}
