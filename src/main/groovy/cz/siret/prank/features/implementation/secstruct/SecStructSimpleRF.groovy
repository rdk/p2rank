package cz.siret.prank.features.implementation.secstruct

import cz.siret.prank.domain.Protein
import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.ResidueFeatureCalculationContext
import cz.siret.prank.features.api.ResidueFeatureCalculator
import groovy.transform.CompileStatic

/**
 * Secondary structure Residue Feature
 */
@CompileStatic
class SecStructSimpleRF extends ResidueFeatureCalculator {

    final List<String> HEADER = SsSimpleHistogram.header

    @Override
    String getName() {
        return 'sss'
    }

    @Override
    List<String> getHeader() {
        return HEADER
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        protein.assignSecondaryStructure()
    }

    @Override
    double[] calculateForResidue(Residue residue, ResidueFeatureCalculationContext context) {
        double[] res = new double[HEADER.size()]

        Residue.SsInfo ss = residue.ss

        SsSimpleHistogram.encodeOneHotInplace(SimpleSecStructType.from(ss.type), res, 0)

        return res
    }

}
