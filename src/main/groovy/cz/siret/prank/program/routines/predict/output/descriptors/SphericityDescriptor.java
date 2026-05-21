package cz.siret.prank.program.routines.predict.output.descriptors;

import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;
import org.biojava.nbio.structure.Atom;

import java.util.BitSet;
import java.util.List;

/**
 * Bounding-sphere sphericity:
 * {@code V_pocket / V_bounding_sphere}, in [0, 1].
 *
 * <p>The bounding sphere is centered at the centroid of the pocket's
 * assigned grid points (NOT {@code pocket.getCentroid()}, which is derived
 * from surface atoms and would give misleading numbers for asymmetric
 * pockets). Its radius is the max distance from that centroid to any
 * assigned grid point.
 *
 * <p>Quantization-free (no surface-area approximation): the value is exactly
 * the volume ratio, so 1.0 = perfect sphere, low values = elongated /
 * irregular.
 *
 * <p>Degenerate pockets ({@code n ≤ 1}) return {@code 0.0} — consistent with
 * {@code volume}, {@code radius_of_gyration}, {@code principal_moments},
 * which all degrade to 0 on insufficient input.
 */
public final class SphericityDescriptor extends AbstractScalarPocketDescriptor {

    @Override public String name() { return "sphericity"; }
    @Override protected ColumnType scalarType() { return ColumnType.DOUBLE; }

    @Override
    protected double computeScalar(PocketGridContext ctx) {
        BitSet indices = ctx.gridPointIndices();
        int n = indices.cardinality();
        if (n == 0) return 0.0d;

        List<Atom> allPoints = ctx.grid().getAllPoints().list;

        double[] c = GridPointStats.centroid(indices, allPoints, n);
        double cx = c[0], cy = c[1], cz = c[2];

        // Max distance from centroid to any assigned point — bounding-sphere radius.
        double maxSqr = 0d;
        for (int i = indices.nextSetBit(0); i >= 0; i = indices.nextSetBit(i + 1)) {
            Atom p = allPoints.get(i);
            double dx = p.getX() - cx, dy = p.getY() - cy, dz = p.getZ() - cz;
            double d2 = dx*dx + dy*dy + dz*dz;
            if (d2 > maxSqr) maxSqr = d2;
        }
        double r = Math.sqrt(maxSqr);
        if (r <= 0d) return 0.0d;  // degenerate: single point — consistent with other descriptors

        double s = ctx.grid().getSpacing();
        double vPocket = n * s * s * s;
        double vSphere = (4d / 3d) * Math.PI * r * r * r;
        double ratio = vPocket / vSphere;

        // Defensive clamp (V_pocket ≤ V_bounding_sphere by construction).
        if (ratio < 0d) return 0d;
        if (ratio > 1d) return 1d;
        return ratio;
    }

}
