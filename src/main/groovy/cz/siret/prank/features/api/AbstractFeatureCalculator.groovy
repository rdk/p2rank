package cz.siret.prank.features.api

import cz.siret.prank.domain.Protein
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic

/**
 * Common FeatureCalculator implementation base.
 *
 * To implement new features extend AtomFeatureCalculator or SasFeatureCalculator.
 */
@CompileStatic
abstract class AbstractFeatureCalculator implements FeatureCalculator, Parametrized {

    /**
     * Default implementation for single value features. Override for multi-value features.
     *
     * Multi value features must return header.
     * Elements of the header should be alpha-numeric strings without whitespace.
     * Header must have the same length as results of calculateForAtom().
     * calculateForAtom() must return array of the same length for every atom and protein.
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
