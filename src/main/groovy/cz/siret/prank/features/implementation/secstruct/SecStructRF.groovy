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
class SecStructRF extends ResidueFeatureCalculator {

    final List<String> HEADER =
            ['sect_len', 'abs_pos', 'rel_pos', 'abs_pos_from_side', 'rel_pos_from_side'] + SsHistogram.header


    @Override
    String getName() {
        return 'ss'
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
        int len = ss.section.length
        int pos = ss.posInSection + 1
        double relPos = ss.relativePosInSection
        int absPosSide = Math.min(pos, Math.abs(len-pos)+1)
        double relPosSide = 0.5d - Math.abs(relPos - 0.5d)

        res[0] = len
        res[1] = pos
        res[2] = relPos
        res[3] = absPosSide
        res[4] = relPosSide

        SsHistogram.encodeOneHotInplace(ss.type, res, 5)

        return res
    }

}
