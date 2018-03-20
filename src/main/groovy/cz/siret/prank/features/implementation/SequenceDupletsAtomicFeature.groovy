package cz.siret.prank.features.implementation

import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import org.biojava.nbio.structure.Atom

/**
 * Sequence duplet propensities for atom residue
 */
class SequenceDupletsAtomicFeature extends AtomFeatureCalculator implements Parametrized {

    static String NAME = 'seqdupat'

//===========================================================================================================//

    @Override
    String getName() {
        NAME
    }

    @Override
    List<String> getHeader() {
        SequenceDupletsFeature.HEADER
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {

        Residue res = context.protein.getResidueForAtom(proteinSurfaceAtom)

        return SequenceDupletsFeature.calculateForResidue(res)
    }

}
