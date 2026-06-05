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
 * <p>Per-fill knobs are passed as a typed {@link FillKnobs} record (each filler
 * accepts its own variant) rather than via a global config object, so the fillers
 * don't depend on {@code Params} and the parameters are named and type-checked.
 */
public interface PocketShapeFiller {

    /**
     * @param rawShell bitset of indices in {@code grid.getAllPoints()} that fall
     *                 within the pocket's SAS-point cutoff
     * @param grid     the full pocket grid (for lattice-neighbor lookups)
     * @param knobs    this filler's {@link FillKnobs} variant (e.g. {@code FillKnobs.Morph}
     *                 for the morph closer); an unexpected variant is a programming error
     * @return a <b>freshly-allocated</b> bitset of indices after the fill step.
     *         Implementations must not return {@code rawShell} (or share storage
     *         with it) and must not mutate it — the caller owns the returned
     *         BitSet and may mutate it freely (e.g. for per-pocket bookkeeping).
     */
    BitSet fill(BitSet rawShell, PocketGrid grid, FillKnobs knobs);

}
