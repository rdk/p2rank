package cz.siret.prank.features.api

import cz.siret.prank.domain.Protein
import cz.siret.prank.geom.Atoms

/**
 *
 */
class FeatureCalculationLocalContext {

    Protein protein

    /**
     * Solvent exposed atoms of the protein in the local neighbourhood of the given Connolly point (for which we are calculating the feature).
     */
    Atoms neighbourhoodExposedAtoms

}
