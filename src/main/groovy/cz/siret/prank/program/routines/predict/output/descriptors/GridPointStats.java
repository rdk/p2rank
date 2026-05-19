package cz.siret.prank.program.routines.predict.output.descriptors;

import org.biojava.nbio.structure.Atom;

import java.util.BitSet;
import java.util.List;

/**
 * Shared one-pass aggregations over a pocket's assigned grid points.
 *
 * <p>Three descriptors ({@link SphericityDescriptor}, {@link RadiusOfGyrationDescriptor},
 * {@link PrincipalMomentsDescriptor}) each need the centroid of the same
 * {@code BitSet × allPoints}. Extracting the loop here dedupes the iteration
 * and gives each callsite a one-line centroid call.
 */
public final class GridPointStats {

    private GridPointStats() {}

    /**
     * Equal-weighted centroid of {@code allPoints[indices.nextSetBit(0)..]}.
     *
     * @param indices   per-pocket BitSet into {@code allPoints}
     * @param allPoints {@code grid.getAllPoints().list}
     * @param n         caller-provided cardinality (avoid {@code indices.cardinality()}
     *                  re-computation when the caller already has it)
     * @return {@code {cx, cy, cz}}
     */
    public static double[] centroid(BitSet indices, List<Atom> allPoints, int n) {
        double sx = 0d, sy = 0d, sz = 0d;
        for (int i = indices.nextSetBit(0); i >= 0; i = indices.nextSetBit(i + 1)) {
            Atom p = allPoints.get(i);
            sx += p.getX();
            sy += p.getY();
            sz += p.getZ();
        }
        return new double[] { sx / n, sy / n, sz / n };
    }

}
