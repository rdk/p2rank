package cz.siret.prank.features.implementation.sequence

import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import org.biojava.nbio.structure.Atom

/**
 * Sequence duplet propensities for atom residue
 *
 * For propensity calculation see
 * cz.siret.prank.program.routines.AnalyzeRoutine#cmdAaSurfSeqDuplets()
 */
class DupletsPropensityAtomicFeature extends AtomFeatureCalculator implements Parametrized {

    static String NAME = 'seqdupat'

//===========================================================================================================//

    @Override
    String getName() {
        NAME
    }

    @Override
    List<String> getHeader() {
        DupletsPropensityFeature.HEADER
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {

        Residue res = context.protein.getResidueForAtom(proteinSurfaceAtom)

        return DupletsPropensityFeature.calculateForResidue(res)
    }

}
