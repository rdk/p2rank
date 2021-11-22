package cz.siret.prank.features.implementation

import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * 1 if protein atom is solvent exposed, 0 if not
 */
@CompileStatic
class IsExposedAtomFeature extends AtomFeatureCalculator {

    @Override
    String getName() {
        return 'exposed'
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {
        boolean exposed = context.protein.exposedAtoms.contains(proteinSurfaceAtom)
        return [exposed ? 1d : 0d] as double[]
    }
    
}
