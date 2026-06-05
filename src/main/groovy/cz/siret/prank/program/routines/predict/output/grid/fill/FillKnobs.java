package cz.siret.prank.program.routines.predict.output.grid.fill;

/**
 * Typed, strategy-specific knobs for a {@link PocketShapeFiller}. Each filler accepts
 * exactly its own knob record, so the parameters are named and type-checked instead of
 * being passed as generic ints reinterpreted per strategy.
 */
public sealed interface FillKnobs permits FillKnobs.None, FillKnobs.Morph, FillKnobs.Closing {

    /** No fill ({@code NoOpFiller}) — keep the raw shell. */
    record None() implements FillKnobs {}

    /**
     * {@code MorphologicalCloser}: iterative conditional dilation.
     * @param minNeighbors filled-neighbor count (of 26) to promote a candidate cell
     * @param maxIters     iteration cap
     */
    record Morph(int minNeighbors, int maxIters) implements FillKnobs {}

    /**
     * {@code ErodeDilateCloser}: true closing = dilate {@code dilateRadius} layers, then
     * erode {@code erodeRadius}. Symmetric ({@code erodeRadius == dilateRadius}) is
     * boundary-preserving; asymmetric ({@code erodeRadius < dilateRadius}) nets outward growth.
     */
    record Closing(int dilateRadius, int erodeRadius) implements FillKnobs {
        public static Closing symmetric(int radius) { return new Closing(radius, radius); }
    }
}
