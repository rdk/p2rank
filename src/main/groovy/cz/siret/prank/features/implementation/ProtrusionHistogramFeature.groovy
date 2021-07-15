package cz.siret.prank.features.implementation

import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Calculates histogram of protrusion values at different radii
 */
@CompileStatic
class ProtrusionHistogramFeature extends SasFeatureCalculator implements Parametrized {

    static final double MIN_DIST = 4

    @Override
    String getName() {
        return "protr_hist"
    }

    @Override
    List<String> getHeader() {
        int n = params.protr_hist_bins
        return (1..n).collect { name + "." + it }.toList()
    }

    /**
     * bins of equal cutoff steps between <MIN_DIST, params.protrusion_radius>
     *
     * @param sasPoint
     * @param context
     * @return
     */
    @Override
    double[] calculateForSasPoint(Atom sasPoint, SasFeatureCalculationContext context) {
        int n = params.protr_hist_bins
        double maxDist = params.protrusion_radius

        assert n >= 1 : "Value of protr_hist_bins must be at least 1!"

        Atoms atoms = context.extractor.deepLayer

        double[] bins = new double[n]

        if (n == 1) {
            bins[0] = atoms.cutoutSphere(sasPoint, maxDist).count
        } else {
            double step = (params.protrusion_radius - MIN_DIST) / (n - 1)
            double cutoff = maxDist
            for (int i = n - 1; i >= 0; i--) {
                atoms = atoms.cutoutSphere(sasPoint, cutoff)
                bins[i] = atoms.count
                cutoff -= step
            }

            if (!params.protr_hist_cumulative) {
                for (int i = n-1; i > 0; i--) {
                    bins[i] -= bins[i-1]
                }
            }

            if (params.protr_hist_relative) {
                double max = bins[n-1]
                max = (max==0d) ? 1 : max

                for (int i=0; i<n-1; ++i) {
                    if (bins[i] != 0) {
                        bins[i] /= max
                    }
                }
            }
        }

        return bins
    }
    
}
