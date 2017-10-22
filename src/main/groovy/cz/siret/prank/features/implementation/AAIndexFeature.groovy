package cz.siret.prank.features.implementation

import cz.siret.prank.features.api.AtomFeatureCalculationContext
import cz.siret.prank.features.api.AtomFeatureCalculator
import cz.siret.prank.features.tables.PropertyTable
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.PDBUtils
import org.biojava.nbio.structure.Atom

/**
 * Simple single value Atom feature that adds B-factor (temperature factor) from PDB to Atom feature vector.
 */
class AAIndexFeature extends AtomFeatureCalculator {
    
    static final PropertyTable aaIndex   = PropertyTable.parse(Futils.readResource("/tables/aa-index-full.csv")).reverse()

    List<String> propertyNames = aaIndex.propertyNames.toList().toSorted()

    @Override
    String getName() {
        "aa"
    }

    @Override
    List<String> getHeader() {
        return propertyNames
    }

    private Double getTableValue(Atom atom, String property) {
        Double val = aaIndex.getValue(PDBUtils.getAtomResidueCode(atom), property)
        return val==null ? 0d : val
    }

    @Override
    double[] calculateForAtom(Atom atom, AtomFeatureCalculationContext ctx) {
        double[] res = new double[propertyNames.size()]
        int i = 0
        for (String property : propertyNames) {
            res[i] = getTableValue(atom, property)
            i++
        }
        return res
    }

}
