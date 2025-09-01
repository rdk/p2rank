package cz.siret.prank.features.implementation.sidechain


import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * 1 if protein atom is sidechain atom, 0 if backbone atom
 */
@CompileStatic
class IsSidechainAtomFeature extends AtomFeatureCalculator {

    @Override
    String getName() {
        return 'sidechain'
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {
        boolean isBackbone = PdbUtils.isBackboneHeavyAtom(proteinSurfaceAtom)
        return [isBackbone ? 0d : 1d] as double[]
    }
    
}
