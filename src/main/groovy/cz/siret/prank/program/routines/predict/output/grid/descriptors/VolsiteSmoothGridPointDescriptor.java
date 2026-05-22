package cz.siret.prank.program.routines.predict.output.grid.descriptors;

import com.google.common.collect.ImmutableList;
import cz.siret.prank.features.implementation.volsite.VolSiteAtomTable;
import cz.siret.prank.features.implementation.volsite.VolSitePharmacophore;
import cz.siret.prank.features.implementation.volsite.VolSitePharmacophore.AtomProps;
import cz.siret.prank.geom.Atoms;
import cz.siret.prank.program.params.Params;
import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;
import org.biojava.nbio.structure.Atom;

import java.util.List;

/**
 * Gaussian-smoothed VolSite descriptor — same six pharmacophore types as
 * {@link VolsiteGridPointDescriptor}, but each column is a continuous score
 * (sum of {@code exp(-r²/(2σ²))} over nearby protein atoms carrying that type)
 * rather than a 0/1 indicator. Captures both proximity and atom count.
 *
 * <p>{@code σ} = {@code -pocket_grid_volsite_sigma}. The kernel is truncated
 * at {@code 4σ} (contribution below ~{@code exp(-8) ≈ 3.4 × 10⁻⁴}).
 */
public final class VolsiteSmoothGridPointDescriptor implements PocketGridPointDescriptor {

    /** Multiplier on σ for the cutoff radius — controls kernel truncation tail. */
    private static final double CUTOFF_SIGMAS = 4d;

    private static final List<ColumnType> TYPES = ImmutableList.of(
            ColumnType.DOUBLE, ColumnType.DOUBLE, ColumnType.DOUBLE,
            ColumnType.DOUBLE, ColumnType.DOUBLE, ColumnType.DOUBLE);

    @Override public String name() { return "volsite_smooth"; }
    @Override public List<String> columnNames() { return VolSitePharmacophore.COLUMN_NAMES; }
    @Override public List<ColumnType> columnTypes() { return TYPES; }
    @Override public boolean isPocketAgnostic() { return true; }

    @Override
    public void compute(PocketGridPointContext ctx, double[] out, int offset) {
        double sigma = Params.INSTANCE.getPocket_grid_volsite_sigma();
        double cutoff = CUTOFF_SIGMAS * sigma;
        double twoSigmaSqr = 2d * sigma * sigma;

        Atoms nearby = ctx.protein().getProteinAtoms().cutoutSphere(ctx.point(), cutoff);
        Atom point = ctx.point();
        VolSiteAtomTable table = VolSiteAtomTable.forProtein(ctx.protein());

        double aromatic = 0d, cation = 0d, anion = 0d;
        double hydrophobic = 0d, acceptor = 0d, donor = 0d;

        for (Atom a : nearby) {
            AtomProps p = table.get(a);
            // Skip atoms with no pharmacophore type — they contribute nothing.
            if (!(p.aromatic || p.cation || p.anion || p.hydrophobic || p.acceptor || p.donor)) {
                continue;
            }
            double dx = a.getX() - point.getX();
            double dy = a.getY() - point.getY();
            double dz = a.getZ() - point.getZ();
            double r2 = dx*dx + dy*dy + dz*dz;
            double w = Math.exp(-r2 / twoSigmaSqr);

            if (p.aromatic)    aromatic    += w;
            if (p.cation)      cation      += w;
            if (p.anion)       anion       += w;
            if (p.hydrophobic) hydrophobic += w;
            if (p.acceptor)    acceptor    += w;
            if (p.donor)       donor       += w;
        }

        out[offset    ] = aromatic;
        out[offset + 1] = cation;
        out[offset + 2] = anion;
        out[offset + 3] = hydrophobic;
        out[offset + 4] = acceptor;
        out[offset + 5] = donor;
    }

}
