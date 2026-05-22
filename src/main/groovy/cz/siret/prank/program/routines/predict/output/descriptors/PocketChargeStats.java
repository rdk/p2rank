package cz.siret.prank.program.routines.predict.output.descriptors;

import cz.siret.prank.domain.Pocket;
import cz.siret.prank.features.implementation.electrostatics.PartialChargeTable;
import org.biojava.nbio.structure.Atom;

/**
 * One-walk accumulator over {@code pocket.surfaceAtoms} that produces both the
 * net charge and the {positive, negative} split. Shared by
 * {@link PocketNetChargeDescriptor} and {@link PocketChargePolarityDescriptor}
 * so the two iterations don't drift and a single walk covers both descriptors
 * when both are enabled.
 */
public record PocketChargeStats(double netCharge, double positive, double negative) {

    public static PocketChargeStats forPocket(Pocket pocket, PartialChargeTable table) {
        double net = 0d, pos = 0d, neg = 0d;
        for (Atom a : pocket.getSurfaceAtoms()) {
            double q = table.get(a);
            net += q;
            if (q > 0d) pos += q;
            else        neg += -q;
        }
        return new PocketChargeStats(net, pos, neg);
    }
}
