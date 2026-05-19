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
 */
public final class SphericityDescriptor implements PocketDescriptor {

    @Override public String name() { return "sphericity"; }
    @Override public ColumnType columnType() { return ColumnType.DOUBLE; }

    @Override
    public double compute(PocketGridContext ctx) {
        BitSet indices = ctx.gridPointIndices();
        int n = indices.cardinality();
        if (n == 0) return 0.0d;

        List<Atom> allPoints = ctx.grid().getAllPoints().list;

        // Centroid of the assigned grid points.
        double sx = 0d, sy = 0d, sz = 0d;
        for (int i = indices.nextSetBit(0); i >= 0; i = indices.nextSetBit(i + 1)) {
            Atom p = allPoints.get(i);
            sx += p.getX(); sy += p.getY(); sz += p.getZ();
        }
        double cx = sx / n, cy = sy / n, cz = sz / n;

        // Max distance from centroid to any assigned point — bounding-sphere radius.
        double maxSqr = 0d;
        for (int i = indices.nextSetBit(0); i >= 0; i = indices.nextSetBit(i + 1)) {
            Atom p = allPoints.get(i);
            double dx = p.getX() - cx, dy = p.getY() - cy, dz = p.getZ() - cz;
            double d2 = dx*dx + dy*dy + dz*dz;
            if (d2 > maxSqr) maxSqr = d2;
        }
        double r = Math.sqrt(maxSqr);
        if (r <= 0d) return 1.0d;  // degenerate: single point → "perfectly spherical"

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
