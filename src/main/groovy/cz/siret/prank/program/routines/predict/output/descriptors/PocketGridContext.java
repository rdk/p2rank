package cz.siret.prank.program.routines.predict.output.descriptors;

import cz.siret.prank.domain.Pocket;
import cz.siret.prank.domain.Protein;
import cz.siret.prank.program.routines.predict.output.grid.PocketGrid;

import java.util.BitSet;

/**
 * Per-pocket context handed to each {@link PocketDescriptor#compute(PocketGridContext)}
 * call. Bundles everything a descriptor might need.
 *
 * <p>{@code gridPointIndices} are indices in {@code grid.getAllPoints()} assigned
 * to this pocket. {@link BitSet} (not {@code Set<Integer>}) — zero autoboxing
 * on iteration, {@code .cardinality()} for size.
 */
public record PocketGridContext(
        Pocket pocket,
        Protein protein,
        PocketGrid grid,
        BitSet gridPointIndices) {
}
