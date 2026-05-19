package cz.siret.prank.features.implementation.volsite;

import cz.siret.prank.features.api.AtomFeatureCalculationContext;
import cz.siret.prank.features.api.AtomFeatureCalculator;
import org.biojava.nbio.structure.Atom;

import java.util.List;

/**
 *
 */
public class VolsiteFeature extends AtomFeatureCalculator {

    @Override
    public String getName() {
        return "volsite";
    }

    @Override
    public List<String> getHeader() {
        return VolSitePharmacophore.COLUMN_NAMES;
    }

    @Override
    public double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {
        VolSitePharmacophore.AtomProps va = VolSitePharmacophore.getAtomProperties(proteinSurfaceAtom.getName(), context.getResidueCode());

        double[] res = new double[VolSitePharmacophore.COLUMN_NAMES.size()];

        res[0] = va.aromatic ? 1d : 0d;
        res[1] = va.cation ? 1d : 0d;
        res[2] = va.anion ? 1d : 0d;
        res[3] = va.hydrophobic ? 1d : 0d;
        res[4] = va.acceptor ? 1d : 0d;
        res[5] = va.donor ? 1d : 0d;

        return res;
    }

}
