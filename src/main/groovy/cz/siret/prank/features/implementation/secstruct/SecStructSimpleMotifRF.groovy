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
class SecStructSimpleMotifRF extends ResidueFeatureCalculator {

    final List<String> HEADER = SsSimpleTriplet.header

    private final boolean directional

    SecStructSimpleMotifRF(boolean ordered) {
        this.directional = ordered
    }

    @Override
    String getName() {
        return 'sss_motif' + (directional ? '_direct' : '')
    }

    @Override
    List<String> getHeader() {
        return HEADER
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        protein.assignSecondaryStructure()
    }

    Residue.SsSection findPreviousDifferentSimpleSection(Residue residue) {
        SimpleSecStructType type = SimpleSecStructType.from(residue.ss.type)
        Residue.SsSection current = residue.ss.section.previous
        while (current != null && SimpleSecStructType.from(current.type) == type) {
            current = current.previous
        }
        return current
    }

    Residue.SsSection findNextDifferentSimpleSection(Residue residue) {
        SimpleSecStructType type = SimpleSecStructType.from(residue.ss.type)
        Residue.SsSection current = residue.ss.section.next
        while (current != null && SimpleSecStructType.from(current.type) == type) {
            current = current.next
        }
        return current
    }

    @Override
    double[] calculateForResidue(Residue residue, ResidueFeatureCalculationContext context) {

        Residue.SsSection prev   = findPreviousDifferentSimpleSection(residue)
        Residue.SsSection middle = residue.ss.section
        Residue.SsSection next   = findNextDifferentSimpleSection(residue)

        SsSimpleTriplet triplet = SsSimpleTriplet.from(prev, middle, next)

        if (!directional) {
            triplet.order()
        }

        double[] res = new double[HEADER.size()]
        triplet.encodeOneHotInplace(res, 0)
        return res
    }

}
