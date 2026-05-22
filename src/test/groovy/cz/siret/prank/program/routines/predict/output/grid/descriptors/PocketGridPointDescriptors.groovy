package cz.siret.prank.program.routines.predict.output.grid.descriptors

import groovy.transform.CompileStatic

/**
 * Test-only helpers for {@link PocketGridPointDescriptor} implementations.
 *
 * <p>The production SPI is direct-write ({@code void compute(ctx, out, offset)})
 * — tests want a {@code double[]} they can index into for assertions, so
 * {@link #computeArr} wraps the SPI with an allocate-and-return adapter.
 */
@CompileStatic
final class PocketGridPointDescriptors {

    private PocketGridPointDescriptors() {}

    /** Allocate a result buffer sized to {@code d.columnNames().size()}, invoke
     *  the descriptor's direct-write {@code compute}, and return the buffer.
     *  Tests only — production callers write into the row buffer directly. */
    static double[] computeArr(PocketGridPointDescriptor d, PocketGridPointContext ctx) {
        double[] out = new double[d.columnNames().size()]
        d.compute(ctx, out, 0)
        return out
    }
}
