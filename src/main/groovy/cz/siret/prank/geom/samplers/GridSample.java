package cz.siret.prank.geom.samplers;

import cz.siret.prank.geom.Atoms;

/**
 * Output of {@link GridGenerator#sampleGridPointsBetween}: the kept lattice points
 * plus the origin the sampler used to align them. Callers that need to compute
 * lattice coordinates (lookup keys, neighbor offsets) read the origin from here
 * instead of recomputing the same {@code Box.aroundAtoms(...).withMargin(...)}
 * + {@link GridGenerator#shift} pipeline.
 */
public record GridSample(Atoms points, double originX, double originY, double originZ) {
}
