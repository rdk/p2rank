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

    protected List<String> header = [getName()]

    /**
     * default implementation for single value features
     */
    @Override
    List<String> getHeader() {
        return header
    }

    @Override
    void preProcessProtein(Protein protein) {
        // implementation optional
    }

    @Override
    void postProcessProtein(Protein protein) {
        // implementation optional
    }

}
