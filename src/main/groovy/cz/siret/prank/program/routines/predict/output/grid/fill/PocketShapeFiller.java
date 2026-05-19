package cz.siret.prank.program.routines.predict.output.grid.fill;

import cz.siret.prank.program.routines.predict.output.grid.PocketGrid;

import java.util.BitSet;

/**
 * Strategy interface for turning a raw per-pocket shell of grid points into a
 * "shaped" assignment — typically by closing morphological gaps so the
 * resulting region is convex-ish.
 *
 * <p>Implementations should be stateless and thread-safe (multiple pockets are
 * filled independently, potentially in parallel).
 *
 * <p>Per-fill numeric knobs are passed as explicit args (minNeighbors, maxIters)
 * rather than via a global config object so the fillers don't depend on
 * {@code Params} — keeps tests simple and the interface free of irrelevant
 * config for strategies that don't need it.
 */
public interface PocketShapeFiller {

    /**
     * @param rawShell     bitset of indices in {@code grid.getAllPoints()}
     *                     that fall within the pocket's surface-atom cutoff
     * @param grid         the full pocket grid (for lattice-neighbor lookups)
     * @param minNeighbors morph_closing only — neighbor count threshold
     * @param maxIters     morph_closing only — iteration cap
     * @return bitset of indices after the fill step; may equal {@code rawShell}
     *         for no-op strategies. Implementations must not mutate {@code rawShell}.
     */
    BitSet fill(BitSet rawShell, PocketGrid grid, int minNeighbors, int maxIters);

}
