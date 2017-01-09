package cz.siret.prank.features.implementation.example

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.features.api.FeatureCalculator
import cz.siret.prank.features.api.SasFeatureCalculationContext
import org.openscience.cdk.Atom

/**
 *
 */
class ExampleFeatureCalculator extends AtomFeatureCalculator {


    @Override
    FeatureCalculator.Type getType() {
        return null
    }

    @Override
    String getName() {
        return null
    }

    @Override
    List<String> getHeader() {
        return null
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext protein) {
        return new double[0]
    }
}
