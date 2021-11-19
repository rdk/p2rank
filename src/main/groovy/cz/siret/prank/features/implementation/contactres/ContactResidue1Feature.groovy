package cz.siret.prank.features.implementation.contactres

import cz.siret.prank.domain.AA
import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 * Contact residue type (for single nearest residue)
 */
@Slf4j
@CompileStatic
class ContactResidue1Feature extends SasFeatureCalculator implements Parametrized {

    static String NAME = 'cres1'

    static List<AA> AATYPES = AA.values().toList()
    static List<String> HEADER = AATYPES.collect{ it.name() }.toList()

//===========================================================================================================//

    @Override
    String getName() {
        NAME
    }

    @Override
    List<String> getHeader() {
        HEADER
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {

        Residue res = context.protein.residues.findNearest(sasPoint)
        AA aa = res?.aa

        double[] vect = new double[AATYPES.size()]
        if (aa != null) {
            vect[aa.ordinal()] = 1d
        }

        return vect
    }
    
}
