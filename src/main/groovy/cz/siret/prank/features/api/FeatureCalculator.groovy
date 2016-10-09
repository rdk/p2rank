package cz.siret.prank.features.api

import cz.siret.prank.domain.Protein
import org.openscience.cdk.Atom

/**
 * Calculator for a single composite feature. Composite feature consists of the vector of doubles with the header.
 *
 * Should not store state.
 */
interface FeatureCalculator {

    /**
     * Return the header according to current parametrization. Should not change during the feature extraction on the dataset.
     */
    List<String> getHeader()

    /**
     * (Optionally) perform preliminary calculations on the whole protein.
     * Store the calculated data into protein.secondaryData .
     *
     * @param protein
     */
    void prepareProtein(Protein protein)

    /**
     *
     * @param connollyPoint one of the points on the pre-calculated Protein's Connolly Surface
     * @param context
     * @return
     */
    double[] calculateFeature(Atom connollyPoint, FeatureCalculationLocalContext context)

    // postProcessProtein ? e.g. for smoothing
}
