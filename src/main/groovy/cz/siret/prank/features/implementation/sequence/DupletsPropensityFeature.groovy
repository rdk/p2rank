package cz.siret.prank.features.implementation.sequence

import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.ResidueFeatureCalculationContext
import cz.siret.prank.features.api.ResidueFeatureCalculator
import cz.siret.prank.features.implementation.table.PropertyTable
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static cz.siret.prank.utils.Futils.readResource

/**
 * Sequence duplet propensities for closest residue
 *
 * For propensity calculation see
 * cz.siret.prank.program.routines.AnalyzeRoutine#cmdAaSurfSeqDuplets()
 */
@Slf4j
@CompileStatic
class DupletsPropensityFeature extends ResidueFeatureCalculator implements Parametrized {

    static final String PROPERTY = 'propensity'
    PropertyTable table

//    static List<String> HEADER = ['product', 'sum', 'max']
    static List<String> HEADER = ['product']

//===========================================================================================================//

    @Override
    String getName() {
        'duplets'
    }

    @Override
    List<String> getHeader() {
        HEADER
    }

    PropertyTable getTable() {
        if (table == null) {
            table = PropertyTable.parse(
                    readResource("/tables/propensities/$params.feat_propensity_tables/duplets.csv"))
        }
        table
    }

    @Override
    double[] calculateForResidue(Residue res, ResidueFeatureCalculationContext context) {
        String code1 = Residue.safeOrderedCode2(res, res?.previousInChain)
        String code2 = Residue.safeOrderedCode2(res, res?.nextInChain)

        String property = PROPERTY
        double val1 = getTable().getValueOrDefault(code1, property, 0d)
        double val2 = getTable().getValueOrDefault(code2, property, 0d)

        double product = val1*val2
        if (val1 == 0d) {
            product = val2*val2
        } else if (val2 == 0d) {
            product = val1*val1
        }
        double sum = val1 + val2
        double max = Math.max(val1, val2)

        return [product] as double[]
//        return [product, sum, max] as double[]
    }

}
