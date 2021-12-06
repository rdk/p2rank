package cz.siret.prank.features.implementation.table

import cz.siret.prank.domain.Residue
import cz.siret.prank.features.api.ResidueFeatureCalculationContext
import cz.siret.prank.features.api.ResidueFeatureCalculator
import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import groovy.transform.CompileStatic

/**
 * Features from AA index table
 */
@CompileStatic
class AAIndexFeature extends ResidueFeatureCalculator implements Parametrized {
    
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

    private Double getTableValue(Residue residue, String property) {
        aaIndex.getValueOrDefault(residue.correctedCode, property, 0d)
    }

    @Override
    double[] calculateForResidue(Residue residue, ResidueFeatureCalculationContext context) {
        double[] res = new double[header.size()]
        int i = 0
        for (String property : header) {
            res[i] = getTableValue(residue, property)
            i++
        }
        return res
    }
    
}
