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
 * For propensity calculation
 * @see cz.siret.prank.program.routines.AnalyzeRoutine#cmdAaSurfSeqTriplets()
 */
@Slf4j
@CompileStatic
class TripletsPropensityFeature extends SasFeatureCalculator implements Parametrized {

    static final PropertyTable TABLE = PropertyTable.parse(readResource("/tables/peptides/aa-surf-seq-triplets.csv"))
    static final String PROPERTY = 'P864' // TODO parametrize

    static String NAME = 'seqtrip'

    static List<String> HEADER = ['prop','prop2']

//===========================================================================================================//

    @Override
    String getName() {
        NAME
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
        String code = Residue.safeSorted3CodeFor(res)
        double prop = TABLE.getValueOrDefault(code, PROPERTY, 0d)

        return [prop, prop*prop] as double[]
    }
    
}
