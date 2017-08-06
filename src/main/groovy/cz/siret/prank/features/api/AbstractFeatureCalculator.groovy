package cz.siret.prank.features.api

import cz.siret.prank.domain.Protein
import groovy.transform.CompileStatic

/**
 * Common FeatureCalculator implementation base.
 *
 * To implement new features extend AtomFeatureCalculator or SasFeatureCalculator.
 */
@CompileStatic
abstract class AbstractFeatureCalculator implements FeatureCalculator {

    /**
     * Default implementation for single value features. Override for multi-value features.
     */
    @Override
    List<String> getHeader() {
        return [getName()]
    }

    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        // implementation optional
    }

    @Override
    void postProcessProtein(Protein protein) {
        // implementation optional
    }

}
