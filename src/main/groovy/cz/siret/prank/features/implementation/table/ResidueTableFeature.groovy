package cz.siret.prank.features.implementation.table

import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import static cz.siret.prank.utils.Futils.readResource

/**
 *
 */
@Slf4j
@CompileStatic
class ResidueTableFeature extends AtomFeatureCalculator implements Parametrized {

    public static final String NAME = 'residue_table'

    private static final double[] EMPTY = new double[0]

    @Override
    String getName() {
        return NAME
    }

    @Override
    List<String> getHeader() {
        return params.residue_table_features
    }

//===========================================================================================================//

    static final PropertyTable aa5FactorsTable      = PropertyTable.parse(readResource("/tables/aa-5factors.csv"))
    static final PropertyTable aa5PropensitiesTable = PropertyTable.parse(readResource("/tables/aa-propensities.csv"))
    static final PropertyTable aaPropertyTable      = aa5FactorsTable.join(aa5PropensitiesTable)

    
    private static Double getResidueTableValue(String residueCode, String property) {
        
        Double val = aaPropertyTable.getValue(residueCode, property)
        // TODO return avg if residueCode not found in table
        return val==null ? 0d : val
    }


    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {
        if (header.size() == 0) {
            return EMPTY
        }

        String residueCode = PdbUtils.getCorrectedAtomResidueCode(proteinSurfaceAtom)

        double[] res = new double[header.size()]

        int i = 0
        for (String property : header) {
            res[i] = getResidueTableValue(residueCode, property)
            i++
        }

        return res
    }

}
