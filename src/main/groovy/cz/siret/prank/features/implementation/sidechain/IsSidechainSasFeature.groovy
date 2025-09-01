package cz.siret.prank.features.implementation.sidechain


import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * 1 if SAS point is nearest to sidechain atom, 0 if nearest to backbone atom
 */
@CompileStatic
class IsSidechainSasFeature extends SasFeatureCalculator {

    @Override
    String getName() {
        return 'sidechain_sas'
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        Atom nearest = context.neighbourhoodAtoms.findNearest(sasPoint)
        boolean isBackbone = PdbUtils.isBackboneHeavyAtom(nearest)
        return [isBackbone ? 0d : 1d] as double[]
    }

}
