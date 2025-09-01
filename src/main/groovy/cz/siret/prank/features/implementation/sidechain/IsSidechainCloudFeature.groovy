package cz.siret.prank.features.implementation.sidechain


import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Calculates the ratio of sidechain atoms in the vicinity of a SAS point.
 * Also returns the number of contact atoms within the defined radius.
 */
@CompileStatic
class IsSidechainCloudFeature extends SasFeatureCalculator {

    private static double CLOUD_RADIUS = 4.5

    final List<String> HEADER = ['contact_atoms','sidechain_ratio']

    @Override
    String getName() {
        return 'sidechain_cloud'
    }

    @Override
    List<String> getHeader() {
        return HEADER
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        Atoms cloud = context.neighbourhoodAtoms.cutoutSphere(sasPoint, CLOUD_RADIUS)

        if (cloud.isEmpty()) {
            return [0d, 0d] as double[]
        }

        long nBackbone = 0
        for (Atom a : cloud) {
            if (PdbUtils.isBackboneHeavyAtom(a)) {
                nBackbone++
            }
        }

        double backboneRatio = (double)nBackbone / cloud.size()
        double sidechainRatio = 1d - backboneRatio


        return [cloud.size(), sidechainRatio] as double[]
    }

}
