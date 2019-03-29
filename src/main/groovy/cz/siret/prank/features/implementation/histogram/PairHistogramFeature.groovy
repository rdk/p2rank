package cz.siret.prank.features.implementation.histogram

import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.biojava.nbio.structure.Atom

import static cz.siret.prank.geom.Struct.dist

/**
 * Atom pair distance histogram feature
 */
@Slf4j
@CompileStatic
class PairHistogramFeature extends SasFeatureCalculator implements Parametrized {

    @Override
    String getName() {
        return "pair_hist"
    }

    @Override
    List<String> getHeader() {
        int n = params.pair_hist_bins
        return (1..n).collect { name + "." + it }.toList()
    }

    private Atoms getAtoms(Atom sasPoint, SasFeatureCalculationContext context) {
        if (params.pair_hist_deep) {
            return context.extractor.deepLayer.cutoutSphere(sasPoint, params.pair_hist_radius)
        } else {
            return context.protein.exposedAtoms.cutoutSphere(sasPoint, params.pair_hist_radius)
        }
    }

    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {

        final List<Atom> atoms = getAtoms(sasPoint, context).list
        final DistancePairHist hist = new DistancePairHist(params.pair_hist_bins, 0, params.pair_hist_radius * 2, params.pair_hist_smooth)

        int n = atoms.size()
        if (params.pair_hist_subsample_limit == 0                          // all
                || params.pair_hist_subsample_limit >= (n*(n-1)/2)) {
            for (int i=0; i!=n; ++i) {
                for (int j=i; j!=n; ++j) {
                    hist.add( dist(atoms[i], atoms[j]) )
                }
            }
        } else {                                                          // subsample of random pairs
            final Random rand = new Random(params.seed)
            final int limit = params.pair_hist_subsample_limit
            int c = 0
            while (c < limit) {
                int i = rand.nextInt(n)
                int j = rand.nextInt(n)
                
                hist.add( dist(atoms[i], atoms[j]) )
                c++
            }
        }

        double[] bins = hist.bins
        if (params.pair_hist_normalize) {
            bins = hist.normalizedBins
        }

        return bins
    }

}
