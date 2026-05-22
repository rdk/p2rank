package cz.siret.prank.program.routines.predict.output.descriptors;

import com.google.common.collect.ImmutableList;
import cz.siret.prank.features.implementation.electrostatics.CoulombKernel;
import cz.siret.prank.features.implementation.electrostatics.PartialChargeTable;
import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;

import java.util.List;

/**
 * Three-column polarity summary of the pocket-lining atoms' partial charges,
 * in elementary charge units ({@code e}):
 *
 * <ul>
 *   <li>{@code positive} = Σ max(qᵢ, 0) — total cationic charge</li>
 *   <li>{@code negative} = Σ max(−qᵢ, 0) — total anionic charge (returned as positive magnitude)</li>
 *   <li>{@code ratio} = (positive − negative) / (positive + negative + ε) ∈ [−1, 1] —
 *       normalised polarity; distinguishes neutral pockets (low magnitudes) from
 *       bipolar pockets (large cancelling charges that mask each other in the net)</li>
 * </ul>
 *
 * <p>Iterates {@code pocket.surfaceAtoms} only — no kd-tree query.
 *
 * <p>Companion to {@link PocketNetChargeDescriptor}: the net charge is
 * {@code positive − negative}, but the explicit split (+ ratio) preserves the
 * bipolar-vs-neutral signal that gets lost in pure subtraction.
 */
public final class PocketChargePolarityDescriptor implements PocketDescriptor {

    private static final List<String> COLUMN_NAMES = ImmutableList.of("positive", "negative", "ratio");
    private static final List<ColumnType> TYPES = ImmutableList.of(
            ColumnType.DOUBLE, ColumnType.DOUBLE, ColumnType.DOUBLE);

    @Override public String name() { return "pocket_charge_polarity"; }
    @Override public List<String> columnNames() { return COLUMN_NAMES; }
    @Override public List<ColumnType> columnTypes() { return TYPES; }
    @Override public boolean needsGrid() { return false; }

    @Override
    public double[] compute(PocketGridContext ctx) {
        PocketChargeStats s = PocketChargeStats.forPocket(
                ctx.pocket(), PartialChargeTable.forProtein(ctx.protein()));
        return new double[]{
                s.positive(), s.negative(),
                CoulombKernel.polarityRatio(s.positive(), s.negative())
        };
    }
}
