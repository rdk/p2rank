package cz.siret.prank.program.routines.predict.output.descriptors;

import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;
import org.biojava.nbio.structure.Atom;

import java.util.BitSet;
import java.util.List;

/**
 * Radius of gyration of the pocket's grid points:
 * {@code Rg = sqrt(mean(|r_i - r_cm|²))} where {@code r_cm} is the centroid of the
 * grid points (equal-weight, no atomic mass factor — the points are not atoms).
 *
 * <p>Unit: Å. Complements {@code sphericity} as a shape descriptor: where sphericity
 * captures compactness as a normalized ratio in [0,1], Rg gives the absolute spatial
 * extent of the pocket. Two pockets with the same volume can have very different Rg
 * (compact vs. elongated). {@code Rg = 0} for an empty or single-point pocket.
 */
public final class RadiusOfGyrationDescriptor extends AbstractScalarPocketDescriptor {

    @Override public String name() { return "radius_of_gyration"; }
    @Override protected ColumnType scalarType() { return ColumnType.DOUBLE; }

    @Override
    protected double computeScalar(PocketGridContext ctx) {
        BitSet indices = ctx.gridPointIndices();
        int n = indices.cardinality();
        if (n < 2) return 0.0d;

        List<Atom> allPoints = ctx.grid().getAllPoints().list;

        double[] c = GridPointStats.centroid(indices, allPoints, n);
        double cx = c[0], cy = c[1], cz = c[2];

        // Mean squared distance from centroid.
        double sumSqr = 0d;
        for (int i = indices.nextSetBit(0); i >= 0; i = indices.nextSetBit(i + 1)) {
            Atom p = allPoints.get(i);
            double dx = p.getX() - cx, dy = p.getY() - cy, dz = p.getZ() - cz;
            sumSqr += dx*dx + dy*dy + dz*dz;
        }
        return Math.sqrt(sumSqr / n);
    }

}
