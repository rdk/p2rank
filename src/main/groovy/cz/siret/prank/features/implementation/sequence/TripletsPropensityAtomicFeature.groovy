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

import static cz.siret.prank.features.implementation.sequence.TripletsPropensityFeature.calculatePropensityForResidue
import static cz.siret.prank.utils.Futils.readResource

/**
 * Sequence duplet propensities for closest residue
 *
 * For propensity calculation
 * @see cz.siret.prank.program.routines.AnalyzeRoutine#cmdAaSurfSeqTriplets()
 */
@Slf4j
@CompileStatic
class TripletsPropensityAtomicFeature extends SasFeatureCalculator implements Parametrized {

    static List<String> HEADER = ['prop','prop^2']

//===========================================================================================================//

    @Override
    String getName() {
        'seqtripat'
    }

    @Override
    List<String> getHeader() {
        HEADER
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {

        Residue res = context.protein.residues.findNearest(sasPoint)
        double prop = calculatePropensityForResidue(res)

        return [prop, prop*prop] as double[]
    }

}
