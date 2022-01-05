package cz.siret.prank.features

import cz.siret.prank.domain.AA
import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.ResidueFeatureCalculationContext
import cz.siret.prank.features.api.ResidueFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic

/**
 * One hot encoding for residue type 
 */
@CompileStatic
class ResidueTypeFeature extends ResidueFeatureCalculator implements Parametrized {

    static List<AA> AATYPES = AA.values().toList()
    static List<String> HEADER = AATYPES.collect{ it.name() }.toList()

    @Override
    String getName() {
        "rtype"
    }

    @Override
    List<String> getHeader() {
        return HEADER
    }

    @Override
    double[] calculateForResidue(Residue residue, ResidueFeatureCalculationContext context) {
        AA aa = residue.aa

        double[] vect = new double[AATYPES.size()]
        if (aa != null) {
            vect[aa.ordinal()] = 1d
        }

        return vect
    }
    
}
