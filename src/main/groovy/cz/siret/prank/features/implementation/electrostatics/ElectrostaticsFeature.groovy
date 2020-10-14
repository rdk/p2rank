package cz.siret.prank.features.implementation.electrostatics

import cz.siret.prank.domain.Protein
import cz.siret.prank.features.api.ProcessedItemContext
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Writable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 *
 */
@Slf4j
@CompileStatic
class ElectrostaticsFeature extends SasFeatureCalculator implements Parametrized, Writable {

    private static String ELECTROSTATICS_CUBE_KEY = "electrostatics_cube";

    @Override
    String getName() {
        return 'electrostatics'
    }


    @Override
    void preProcessProtein(Protein protein, ProcessedItemContext context) {
        if (!protein.secondaryData.containsKey(ELECTROSTATICS_CUBE_KEY)) {

        }
        protein.ensureConservationLoaded(context)
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        return new double[0]
    }
}
