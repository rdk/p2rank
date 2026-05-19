package cz.siret.prank.program.routines.predict.output.descriptors;

import com.google.common.collect.ImmutableList;
import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.biojava.nbio.structure.Atom;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * Principal moments of the pocket's grid-point distribution — three eigenvalues
 * of the gyration tensor (equal-weighted PCA on the grid points, centered at the
 * grid-point centroid).
 *
 * <p>Output columns (DOUBLE), sorted descending:
 * <ul>
 *   <li>{@code principal_moments.lambda1} — largest eigenvalue (Å²)</li>
 *   <li>{@code principal_moments.lambda2} — middle eigenvalue (Å²)</li>
 *   <li>{@code principal_moments.lambda3} — smallest eigenvalue (Å²)</li>
 * </ul>
 *
 * <p>Shape signatures:
 * <ul>
 *   <li>λ₁ ≈ λ₂ ≈ λ₃ → spherical pocket</li>
 *   <li>λ₁ ≫ λ₂, λ₃ → elongated (rod-like)</li>
 *   <li>λ₁ ≈ λ₂ ≫ λ₃ → flat (disk-like)</li>
 * </ul>
 *
 * <p>Sum of the three eigenvalues equals {@code radius_of_gyration²} —
 * pairs naturally with the existing {@code radius_of_gyration} descriptor.
 *
 * <p>This is the canonical motivating case for the multi-column descriptor
 * interface: a single eigendecomposition produces all three values, so
 * splitting into three scalar descriptors would re-do the decomposition
 * thrice per pocket.
 *
 * <p>Empty / degenerate pockets:
 * <ul>
 *   <li>0 points → all zeros</li>
 *   <li>1 point  → all zeros (point coincides with the centroid)</li>
 *   <li>collinear points → λ₃ = 0 (one eigenvector is degenerate)</li>
 *   <li>coplanar points  → λ₃ = 0 (one eigenvector lies along the plane normal)</li>
 * </ul>
 */
public final class PrincipalMomentsDescriptor implements PocketDescriptor {

    private static final List<String> COLUMN_NAMES = ImmutableList.of("lambda1", "lambda2", "lambda3");
    private static final List<ColumnType> COLUMN_TYPES = ImmutableList.of(
            ColumnType.DOUBLE, ColumnType.DOUBLE, ColumnType.DOUBLE);
    private static final double[] ZEROS = new double[] { 0d, 0d, 0d };

    @Override public String name() { return "principal_moments"; }
    @Override public List<String> columnNames() { return COLUMN_NAMES; }
    @Override public List<ColumnType> columnTypes() { return COLUMN_TYPES; }

    @Override
    public double[] compute(PocketGridContext ctx) {
        BitSet indices = ctx.gridPointIndices();
        int n = indices.cardinality();
        if (n < 2) return ZEROS.clone();  // degenerate — see class javadoc

        List<Atom> allPoints = ctx.grid().getAllPoints().list;

        double[] c = GridPointStats.centroid(indices, allPoints, n);
        double cx = c[0], cy = c[1], cz = c[2];

        // Gyration tensor (3x3 symmetric): G_ab = (1/n) Σ (r_a - c_a)(r_b - c_b).
        // Accumulate off-diagonal once; the matrix is symmetric.
        double gxx = 0d, gyy = 0d, gzz = 0d, gxy = 0d, gxz = 0d, gyz = 0d;
        for (int i = indices.nextSetBit(0); i >= 0; i = indices.nextSetBit(i + 1)) {
            Atom p = allPoints.get(i);
            double dx = p.getX() - cx, dy = p.getY() - cy, dz = p.getZ() - cz;
            gxx += dx * dx;
            gyy += dy * dy;
            gzz += dz * dz;
            gxy += dx * dy;
            gxz += dx * dz;
            gyz += dy * dz;
        }
        gxx /= n; gyy /= n; gzz /= n; gxy /= n; gxz /= n; gyz /= n;

        // Eigendecomposition of the symmetric 3x3 tensor. EigenDecomposition's symmetric
        // path (used when the matrix is symmetric within its tolerance) returns real
        // eigenvalues in arbitrary order — sort descending below.
        double[][] m = new double[][] {
                { gxx, gxy, gxz },
                { gxy, gyy, gyz },
                { gxz, gyz, gzz }
        };
        EigenDecomposition ed = new EigenDecomposition(new Array2DRowRealMatrix(m, false));
        double[] eigenvalues = ed.getRealEigenvalues();

        // Sort descending. The gyration tensor is positive semi-definite — any tiny
        // negative eigenvalue from numerical noise gets clamped to 0 below.
        Arrays.sort(eigenvalues);
        double l1 = clampNonNegative(eigenvalues[2]);
        double l2 = clampNonNegative(eigenvalues[1]);
        double l3 = clampNonNegative(eigenvalues[0]);
        return new double[] { l1, l2, l3 };
    }

    /**
     * PSD eigenvalues should be ≥ 0; numerical noise can push them slightly negative.
     * NaN is also clamped to 0 as defense-in-depth: {@code NaN < 0} is false, so a NaN
     * eigenvalue would otherwise slip through and propagate to the CSV. The upstream
     * {@code GridGenerator.isFiniteBox} guard already rejects non-finite inputs, but
     * a future code path could bypass it.
     */
    private static double clampNonNegative(double v) {
        return (v < 0d || !Double.isFinite(v)) ? 0d : v;
    }

}
