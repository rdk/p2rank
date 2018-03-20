package cz.siret.prank.features.implementation.sequence

import cz.siret.prank.domain.Residue
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
 * For propensity calculation see
 * cz.siret.prank.program.routines.AnalyzeRoutine#cmdAaSurfSeqDuplets()
 */
@Slf4j
@CompileStatic
class DupletsPropensityFeature extends SasFeatureCalculator implements Parametrized {

    static final PropertyTable TABLE = PropertyTable.parse(readResource("/tables/peptides/aa-surf-seq-duplets.csv"))
    static final String PROPERTY = 'P864' // TODO parametrize

    static List<String> HEADER = ['product', 'sum', 'max']

//===========================================================================================================//

    @Override
    String getName() {
        'seqdup'
    }

    @Override
    List<String> getHeader() {
        HEADER
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {

        Residue res = context.protein.residues.findNearest(sasPoint)

        return calculateForResidue(res)
    }

    static double[] calculateForResidue(@Nullable Residue res) {
        String code1 = Residue.safeOrderedCode2(res, res?.previousInChain)
        String code2 = Residue.safeOrderedCode2(res, res?.nextInChain)

        String property = PROPERTY
        double val1 = TABLE.getValueOrDefault(code1, property, 0d)
        double val2 = TABLE.getValueOrDefault(code2, property, 0d)

        double product = val1*val2
        if (val1 == 0d) {
            product = val2*val2
        } else if (val2 == 0d) {
            product = val1*val1
        }
        double sum = val1 + val2
        double max = Math.max(val1, val2)

        return [product, sum, max] as double[]
    }
    
}
