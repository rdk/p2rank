package cz.siret.prank.features.implementation.chem

import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 *
 */
@CompileStatic
class ChemFeature extends AtomFeatureCalculator {

    static final String NAME = "chem"

    @Override
    String getName() { NAME }

    @Override
    List<String> getHeader() {
        return ChemVector.getHeader()
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {

        ChemVector cv = ChemVector.forAtom(proteinSurfaceAtom, context.residueCode)

        cv.toArray()
    }
    
}
