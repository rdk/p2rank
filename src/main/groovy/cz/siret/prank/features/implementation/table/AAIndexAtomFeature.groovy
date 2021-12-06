package cz.siret.prank.features.implementation.table

import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.PdbUtils
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Features from AA index table
 */
@Deprecated
@CompileStatic
class AAIndexAtomFeature extends AtomFeatureCalculator implements Parametrized {
    
    static final PropertyTable aaIndex   = PropertyTable.parse(Futils.readResource("/tables/aa-index-full.csv")).reverse()

    static final List<String> allPropertyNames = aaIndex.propertyNames.toList().toSorted()


    @Override
    String getName() {
        "aa"
    }

    @Override
    List<String> getHeader() {
        return params.feat_aa_properties ?: []
    }

    private Double getTableValue(Atom atom, String property) {
        Double val = aaIndex.getValue(PdbUtils.getCorrectedAtomResidueCode(atom), property)
        return val==null ? 0d : val
    }

    @Override
    double[] calculateForAtom(Atom atom, AtomFeatureCalculationContext ctx) {
        double[] res = new double[header.size()]
        int i = 0
        for (String property : header) {
            res[i] = getTableValue(atom, property)
            i++
        }
        return res
    }

}
