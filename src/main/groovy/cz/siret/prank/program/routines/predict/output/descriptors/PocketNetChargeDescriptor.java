package cz.siret.prank.program.routines.predict.output.descriptors;

import cz.siret.prank.features.implementation.electrostatics.PartialChargeTable;
import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;

/**
 * Sum of AMBER ff14SB partial charges of the pocket-lining atoms, in
 * elementary charge units ({@code e}).
 *
 * <p>The simplest top-level summary of pocket polarity: positive net charge
 * marks an anion-binding site, negative marks a cation-binding site,
 * near-zero marks a neutral / hydrophobic site.
 *
 * <p>Iterates {@code pocket.surfaceAtoms} only — no kd-tree query.
 *
 * <p>Two pockets with identical {@code pocket_net_charge ≈ 0} can still
 * differ in their +/− split — see {@link PocketChargePolarityDescriptor}
 * for the bipolar/neutral distinction.
 */
public final class PocketNetChargeDescriptor extends AbstractScalarPocketDescriptor {

    @Override public String name() { return "pocket_net_charge"; }
    @Override protected ColumnType scalarType() { return ColumnType.DOUBLE; }
    @Override public boolean needsGrid() { return false; }

    @Override
    protected double computeScalar(PocketGridContext ctx) {
        return PocketChargeStats.forPocket(
                ctx.pocket(),
                PartialChargeTable.forProtein(ctx.protein())
        ).netCharge();
    }
}
