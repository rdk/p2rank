package cz.siret.prank.features.implementation.table

import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

/**
 *
 */
@Slf4j
@CompileStatic
class AtomTableFeature extends AtomFeatureCalculator implements Parametrized {

    public static final String NAME = 'atom_table'

    private static final double[] EMPTY = new double[0]

    @Override
    String getName() {
        return NAME
    }

    @Override
    List<String> getHeader() {
        return params.atom_table_features
    }

//===========================================================================================================//

    static final PropertyTable atomPropertyTable = PropertyTable.parseResource("/tables/atomic-properties.csv")


    private static double getAtomTableValue(String atomName, String property) {

        Double val = atomPropertyTable.getValue(atomName, property)
        // TODO return avg if atomName not found in table
        return val==null ? 0d : val
    }

    static double transformValue(double val, double power, boolean keepSgn) {
        if (power != 1d) {
            if (keepSgn) {
                val = Math.signum(val) *  Math.pow(Math.abs(val), power)
            } else {
                val = Math.pow(val, power)
            }
        }
        return val
    }


    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {
        List<String> header = this.header
        if (header.size() == 0) {
            return EMPTY
        }

        double ATOM_POW = params.atom_table_feat_pow
        boolean KEEP_SGN = params.atom_table_feat_keep_sgn

        String atomName = PdbUtils.getAtomTypeInResidueCode(proteinSurfaceAtom)

        double[] res = new double[header.size()]

        int i = 0
        for (String property : header) {
            double val = getAtomTableValue(atomName, property)

            val = transformValue(val, ATOM_POW, KEEP_SGN)

            res[i] = val
            i++
        }

        return res
    }

}
