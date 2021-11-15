package cz.siret.prank.features.implementation.volsite

import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 *
 */
@CompileStatic
class VolsiteClosestAtomSasFeature extends SasFeatureCalculator {

    private static final List<String> HEADER = [
        "vsAromatic",
        "vsCation",
        "vsAnion",
        "vsHydrophobic",
        "vsAcceptor",
        "vsDonor"
    ]

    @Override
    String getName() { 'volsite' }

    @Override
    List<String> getHeader() {
        return HEADER
    }

    @Override
    static double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {
        VolSitePharmacophore.AtomProps va = VolSitePharmacophore.getAtomProperties(proteinSurfaceAtom.name, context.residueCode)

        double[] res = new double[HEADER.size()]

        res[0] = va.aromatic     ? 1d : 0d
        res[1] = va.cation       ? 1d : 0d
        res[2] = va.anion        ? 1d : 0d
        res[3] = va.hydrophobic  ? 1d : 0d
        res[4] = va.acceptor     ? 1d : 0d
        res[5] = va.donor        ? 1d : 0d

        res
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {
        VolSitePharmacophore.AtomProps va = VolSitePharmacophore.getAtomProperties(proteinSurfaceAtom.name, context.residueCode)

        double[] res = new double[HEADER.size()]

        res[0] = va.aromatic     ? 1d : 0d
        res[1] = va.cation       ? 1d : 0d
        res[2] = va.anion        ? 1d : 0d
        res[3] = va.hydrophobic  ? 1d : 0d
        res[4] = va.acceptor     ? 1d : 0d
        res[5] = va.donor        ? 1d : 0d

        res
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        context.protein.proteinAtoms
        return new double[0]
    }
}
