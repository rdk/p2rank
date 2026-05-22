package cz.siret.prank.program.routines.predict.output.grid.descriptors;

import com.google.common.collect.ImmutableList;
import cz.siret.prank.features.implementation.electrostatics.CoulombKernel;
import cz.siret.prank.features.implementation.electrostatics.PartialChargeTable;
import cz.siret.prank.geom.Atoms;
import cz.siret.prank.program.params.Params;
import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;
import org.biojava.nbio.structure.Atom;

import java.util.List;

/**
 * Per-grid-point electrostatics descriptor — 5 columns of Coulomb-flavour
 * scalars summarising the charged-atom environment at each point within
 * {@code pocket_grid_electrostatic_radius}:
 *
 * <ul>
 *   <li>{@code potential} = Σ qᵢ / rᵢ  (signed, units e/Å) — net potential</li>
 *   <li>{@code field_magnitude} = ‖Σ qᵢ · r⃗ᵢ / rᵢ³‖ (units e/Å²) — field vector magnitude</li>
 *   <li>{@code positive} = Σ max(qᵢ, 0) / rᵢ (units e/Å) — cationic environment</li>
 *   <li>{@code negative} = Σ max(−qᵢ, 0) / rᵢ (units e/Å) — anionic environment</li>
 *   <li>{@code polarity} = (positive − negative) / (positive + negative + ε) ∈ [−1, 1] —
 *       normalised polarity, distinguishes neutral (low magnitudes) from bipolar
 *       (large cancelling charges)</li>
 * </ul>
 *
 * <p>All five share one kd-tree neighborhood query per point (one {@code cutoutSphere})
 * and one neighbor walk via {@link CoulombKernel}. The 1/r is clamped to
 * {@code electrostatics_min_r} to avoid singularities at vdW overlap.
 *
 * <p>Pocket-agnostic: depends only on (point, protein), not on which pocket
 * the point belongs to — so the {@code PocketGridRows} memo computes once
 * per pointIdx regardless of multi-pocket overlap.
 */
public final class ElectrostaticsGridPointDescriptor implements PocketGridPointDescriptor {

    private static final List<String> COLUMN_NAMES = ImmutableList.of(
            "potential", "field_magnitude", "positive", "negative", "polarity");

    private static final List<ColumnType> TYPES = ImmutableList.of(
            ColumnType.DOUBLE, ColumnType.DOUBLE, ColumnType.DOUBLE,
            ColumnType.DOUBLE, ColumnType.DOUBLE);

    @Override public String name() { return "electrostatics"; }
    @Override public List<String> columnNames() { return COLUMN_NAMES; }
    @Override public List<ColumnType> columnTypes() { return TYPES; }
    @Override public boolean isPocketAgnostic() { return true; }

    @Override
    public void compute(PocketGridPointContext ctx, double[] out, int offset) {
        Params p = Params.INSTANCE;
        PartialChargeTable table = PartialChargeTable.forProtein(ctx.protein());
        Atoms nearby = ctx.protein().getProteinAtoms()
                .cutoutSphere(ctx.point(), p.getElectrostatics_radius());
        CoulombKernel.Result r = CoulombKernel.accumulate(
                ctx.point(), nearby, table, p.getElectrostatics_min_r());
        out[offset    ] = r.potential();
        out[offset + 1] = r.fieldMagnitude();
        out[offset + 2] = r.positive();
        out[offset + 3] = r.negative();
        out[offset + 4] = CoulombKernel.polarityRatio(r.positive(), r.negative());
    }
}
