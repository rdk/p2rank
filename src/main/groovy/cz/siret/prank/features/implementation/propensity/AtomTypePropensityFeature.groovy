package cz.siret.prank.features.implementation.propensity

import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.features.implementation.table.PropertyTable
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import javax.annotation.Nonnull

/**
 * Atom type propensity
 *
 * For propensity calculation
 * @see cz.siret.prank.program.routines.analyze.AnalyzeRoutine#cmdAtomTypePropensities()
 */
@Slf4j
@CompileStatic
class AtomTypePropensityFeature extends AtomFeatureCalculator implements Parametrized {

    static final String PROPERTY = 'propensity'
    PropertyTable table 

    //static List<String> HEADER = ['prop', 'prop^2']
    static List<String> HEADER = ['prop']

//===========================================================================================================//

    @Override
    String getName() {
        'atomtype-propensity'
    }

    @Override
    List<String> getHeader() {
        HEADER
    }

    PropertyTable getTable() {
        if (table == null) {
            table = PropertyTable.parseResource("/tables/propensities/$params.feat_propensity_tables/atomtype-propensity.csv")
        }
        table
    }

    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {
        double prop = calculatePropensityForAtom(proteinSurfaceAtom)

        //return [prop, prop*prop] as double[]
        return [prop] as double[]
    }

    double calculatePropensityForAtom(@Nonnull Atom atom) {
        String code = PdbUtils.getAtomTypeInResidueCode(atom)
        return getTable().getValueOrDefault(code, PROPERTY, 0d)
    }
    
}
