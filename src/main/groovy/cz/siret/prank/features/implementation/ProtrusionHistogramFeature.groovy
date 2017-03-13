package cz.siret.prank.features.implementation

import cz.siret.prank.features.api.SasFeatureCalculationContext
import cz.siret.prank.features.api.SasFeatureCalculator
import cz.siret.prank.geom.Atoms
import cz.siret.prank.program.params.Parametrized
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 *
 */
@CompileStatic
class ProtrusionHistogramFeature extends SasFeatureCalculator implements Parametrized {

    static final double MIN_DIST = 2d

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

        assert n>=2 : "Value of protr_hist_bins must be at least 2!"

        Atoms atoms = context.extractor.deepSurrounding

        double[] bins = new double[n]

        double step = (params.protrusion_radius - MIN_DIST) / (n - 1)
        double cutoff = maxDist
        for (int i = n - 1; i >= 0; i--) {
            atoms =  atoms.cutoffAtomsAround(sasPoint, cutoff)
            bins[i] = atoms.count
            cutoff -= step
        }

        return bins
    }
}
