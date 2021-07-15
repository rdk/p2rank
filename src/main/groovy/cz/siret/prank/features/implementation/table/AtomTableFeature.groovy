package cz.siret.prank.features.implementation.table

import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
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

    static final PropertyTable atomPropertyTable = PropertyTable.parse(Futils.readResource("/tables/atomic-properties.csv"))


    private static Double getAtomTableValue(String atomName, String property) {

        Double val = atomPropertyTable.getValue(atomName, property)
        // TODO return avg if atomName not found in table
        return val==null ? 0d : val
    }


    @Override
    double[] calculateForAtom(Atom proteinSurfaceAtom, AtomFeatureCalculationContext context) {
        if (header.size() == 0) {
            return EMPTY
        }

        double ATOM_POW = params.atom_table_feat_pow
        boolean KEEP_SGN = params.atom_table_feat_keep_sgn

        String atomName = PdbUtils.getCorrectedAtomResidueCode(proteinSurfaceAtom) + "." + proteinSurfaceAtom.name

        double[] res = new double[header.size()]

        int i = 0
        for (String property : header) {
            double val = getAtomTableValue(atomName, property)

            if (ATOM_POW != 1d) {
                if (KEEP_SGN) {
                    val = Math.signum(val) * Math.abs( Math.pow(val, ATOM_POW) )
                } else {
                    val = Math.pow(val, ATOM_POW)
                }
            }

            res[i] = val
            i++
        }

        return res
    }

}
