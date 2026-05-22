package cz.siret.prank.features.implementation.electrostatics;

import cz.siret.prank.geom.Atoms;
import org.biojava.nbio.structure.Atom;

/**
 * Shared inner-loop math for electrostatics features. Used by
 * {@link ElectrostaticsSasFeature} and {@link cz.siret.prank.program.routines.predict.output.grid.descriptors.ElectrostaticsGridPointDescriptor}
 * so the formulas live in one place and can't drift between layers.
 *
 * <p>One neighbor walk accumulates five quantities sharing the {@code 1/r}
 * computation per neighbor:
 *
 * <pre>
 *   potential       = Σᵢ qᵢ / rᵢ
 *   abs_potential   = Σᵢ |qᵢ| / rᵢ
 *   field_magnitude = ‖Σᵢ qᵢ · r⃗ᵢ / rᵢ³‖
 *   positive        = Σᵢ max(qᵢ, 0) / rᵢ
 *   negative        = Σᵢ max(−qᵢ, 0) / rᵢ
 * </pre>
 *
 * <p>{@code rᵢ} is clamped to {@code minR} to avoid the 1/r singularity when
 * the probe point coincides with an atom's interior.
 *
 * <p>Units: charges in {@code e}, distances in Å, so potential outputs are
 * in {@code e/Å}, field in {@code e/Å²}.
 */
public final class CoulombKernel {

    /** Stabiliser in the polarity normalization — keeps {@link #polarityRatio}
     *  finite for neutral environments where {@code positive + negative ≈ 0}. */
    public static final double POLARITY_EPS = 1e-9d;

    private CoulombKernel() {}

    public record Result(
            double potential,
            double absPotential,
            double fieldMagnitude,
            double positive,
            double negative
    ) {}

    /** Normalised polarity in [−1, 1]: +1 = purely cationic, −1 = purely anionic,
     *  0 = neutral or symmetrically bipolar. Shared by {@code ElectrostaticsGridPointDescriptor}
     *  and {@code PocketChargePolarityDescriptor}. */
    public static double polarityRatio(double positive, double negative) {
        return (positive - negative) / (positive + negative + POLARITY_EPS);
    }

    /**
     * @param point   probe position
     * @param nearby  protein atoms within the cutoff radius (already filtered by caller)
     * @param table   per-protein charge lookup
     * @param minR    minimum atom-to-probe distance, used to clamp 1/r
     */
    public static Result accumulate(Atom point, Atoms nearby, PartialChargeTable table, double minR) {
        double px = point.getX(), py = point.getY(), pz = point.getZ();
        double potential = 0d, abs = 0d, pos = 0d, neg = 0d;
        double fx = 0d, fy = 0d, fz = 0d;
        for (Atom a : nearby) {
            double q = table.get(a);
            double dx = a.getX() - px, dy = a.getY() - py, dz = a.getZ() - pz;
            double r2 = dx*dx + dy*dy + dz*dz;
            double r = Math.sqrt(r2);
            if (r < minR) r = minR;
            double invR = 1d / r;
            double invR3 = invR * invR * invR;
            potential += q * invR;
            // Fold |q| into the sign branch — saves one Math.abs per neighbor and
            // makes the positive/negative/abs accumulators read together.
            if (q > 0) {
                double term = q * invR;
                pos += term; abs += term;
            } else {
                double term = -q * invR;
                neg += term; abs += term;
            }
            double w = q * invR3;
            fx += w * dx; fy += w * dy; fz += w * dz;
        }
        return new Result(potential, abs, Math.sqrt(fx*fx + fy*fy + fz*fz), pos, neg);
    }
}
