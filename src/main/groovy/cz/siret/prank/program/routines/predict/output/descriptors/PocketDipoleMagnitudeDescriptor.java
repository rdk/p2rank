package cz.siret.prank.program.routines.predict.output.descriptors;

import cz.siret.prank.domain.Pocket;
import cz.siret.prank.features.implementation.electrostatics.PartialChargeTable;
import cz.siret.prank.geom.Atoms;
import cz.siret.prank.program.routines.predict.output.TableData.ColumnType;
import org.biojava.nbio.structure.Atom;

/**
 * Magnitude of the dipole moment of the pocket-lining atoms, taken about
 * their own geometric centroid:
 *
 * <pre>
 *   r⃗_cm = (1/N) Σᵢ r⃗ᵢ        (over pocket.surfaceAtoms)
 *   μ⃗ = Σᵢ qᵢ · (r⃗ᵢ − r⃗_cm)
 *   ‖μ⃗‖ = √(μx² + μy² + μz²)
 * </pre>
 *
 * <p>Unit: e·Å (elementary charge × Ångström).
 *
 * <p>Two pockets with identical {@link PocketNetChargeDescriptor} (e.g. both
 * net 0) can have very different dipole magnitudes — a bipolar pocket with
 * cationic and anionic patches on opposite sides has a large dipole, a
 * uniformly neutral pocket has zero. This column separates them.
 *
 * <p>Direction is dropped — pocket-local frames aren't reported, so absolute
 * Cartesian components would be meaningless to a downstream classifier.
 *
 * <p>Origin is the surface-atom centroid (not {@link Pocket#getCentroid()},
 * which the predictor sets from SAS-point clustering — a geometrically
 * different point). For a net-charged pocket the dipole magnitude depends
 * on the origin choice; computing both the centroid and the moment over
 * the same atom set is one defensible convention and keeps the descriptor
 * a function of {@code surfaceAtoms} alone.
 */
public final class PocketDipoleMagnitudeDescriptor extends AbstractScalarPocketDescriptor {

    @Override public String name() { return "pocket_dipole_magnitude"; }
    @Override protected ColumnType scalarType() { return ColumnType.DOUBLE; }
    @Override public boolean needsGrid() { return false; }

    @Override
    protected double computeScalar(PocketGridContext ctx) {
        Atoms surfaceAtoms = ctx.pocket().getSurfaceAtoms();
        if (surfaceAtoms == null || surfaceAtoms.isEmpty()) return 0d;

        Atom centroid = surfaceAtoms.getCentroid();
        double cx = centroid.getX(), cy = centroid.getY(), cz = centroid.getZ();

        PartialChargeTable table = PartialChargeTable.forProtein(ctx.protein());
        double mx = 0d, my = 0d, mz = 0d;
        for (Atom a : surfaceAtoms) {
            double q = table.get(a);
            mx += q * (a.getX() - cx);
            my += q * (a.getY() - cy);
            mz += q * (a.getZ() - cz);
        }
        return Math.sqrt(mx*mx + my*my + mz*mz);
    }
}
