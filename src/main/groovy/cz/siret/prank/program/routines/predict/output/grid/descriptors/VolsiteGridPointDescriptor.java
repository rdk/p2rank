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
 * Per-grid-point VolSite pharmacophore descriptor — 6 indicator columns
 * (one per VolSite type) emitting 1.0 if the nearest protein atom carrying
 * that pharmacophore type is within {@code -pocket_grid_volsite_radius} of
 * the grid point, 0.0 otherwise.
 *
 * <p>Atom-level classification reuses {@link VolSitePharmacophore} — the same
 * source as {@link cz.siret.prank.features.implementation.volsite.VolsiteFeature},
 * so a "1" in {@code volsite.vsCation} here means the same thing as a "1" in
 * {@code vsCation} for an atom feature: a nearby protein atom is a cation
 * pharmacophore under VolSite's rules.
 *
 * <p>The smooth counterpart is {@link VolsiteSmoothGridPointDescriptor}.
 */
public final class VolsiteGridPointDescriptor implements PocketGridPointDescriptor {

    private static final List<ColumnType> TYPES = ImmutableList.of(
            ColumnType.INT, ColumnType.INT, ColumnType.INT,
            ColumnType.INT, ColumnType.INT, ColumnType.INT);

    @Override public String name() { return "volsite"; }
    @Override public List<String> columnNames() { return VolSitePharmacophore.COLUMN_NAMES; }
    @Override public List<ColumnType> columnTypes() { return TYPES; }
    @Override public boolean isPocketAgnostic() { return true; }

    @Override
    public void compute(PocketGridPointContext ctx, double[] out, int offset) {
        double radius = Params.INSTANCE.getPocket_grid_volsite_radius();
        Atoms nearby = ctx.protein().getProteinAtoms().cutoutSphere(ctx.point(), radius);
        VolSiteAtomTable table = VolSiteAtomTable.forProtein(ctx.protein());

        boolean aromatic = false, cation = false, anion = false;
        boolean hydrophobic = false, acceptor = false, donor = false;

        for (Atom a : nearby) {
            AtomProps p = table.get(a);
            if (p.aromatic)    aromatic = true;
            if (p.cation)      cation = true;
            if (p.anion)       anion = true;
            if (p.hydrophobic) hydrophobic = true;
            if (p.acceptor)    acceptor = true;
            if (p.donor)       donor = true;
            // Short-circuit when everything is set — typical for points buried among
            // a mixed pharmacophore residue cluster.
            if (aromatic && cation && anion && hydrophobic && acceptor && donor) break;
        }

        out[offset    ] = aromatic    ? 1d : 0d;
        out[offset + 1] = cation      ? 1d : 0d;
        out[offset + 2] = anion       ? 1d : 0d;
        out[offset + 3] = hydrophobic ? 1d : 0d;
        out[offset + 4] = acceptor    ? 1d : 0d;
        out[offset + 5] = donor       ? 1d : 0d;
    }

}
