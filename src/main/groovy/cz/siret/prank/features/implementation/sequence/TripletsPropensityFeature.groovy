package cz.siret.prank.features.implementation.sequence

import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.ResidueFeatureCalculationContext
import cz.siret.prank.features.api.ResidueFeatureCalculator
import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.features.tables.PropertyTable
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import javax.annotation.Nullable

import static cz.siret.prank.utils.Futils.readResource

/**
 * Sequence duplet propensities for closest residue
 *
 * For propensity calculation
 * @see cz.siret.prank.program.routines.AnalyzeRoutine#cmdAaSurfSeqTriplets()
 */
@Slf4j
@CompileStatic
class TripletsPropensityFeature extends ResidueFeatureCalculator implements Parametrized {

    final PropertyTable TABLE = PropertyTable.parse(
            readResource("/tables/peptides/$params.pept_propensities_set/triplets.csv"))
    final String PROPERTY = 'propensity'

    static List<String> HEADER = ['prop', 'prop^2']

//===========================================================================================================//

    @Override
    String getName() {
        'triplets'
    }

    @Override
    List<String> getHeader() {
        HEADER
    }

    @Override
    double[] calculateForResidue(Residue residue, ResidueFeatureCalculationContext context) {
        double prop = calculatePropensityForResidue(residue)

        return [prop, prop*prop] as double[]
    }

    double calculatePropensityForResidue(@Nullable Residue res) {
        String code = Residue.safeSorted3CodeFor(res)
        double prop = TABLE.getValueOrDefault(code, PROPERTY, 0d)

        return prop
    }
    
}
