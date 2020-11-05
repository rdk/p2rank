package cz.siret.prank.features.implementation.histogram

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Pair distance histogram
 */
@Slf4j
@CompileStatic
class DistancePairHist {

    int size
    double min
    double max
    boolean smooth

    double[] bins
    double step
    int count = 0

    DistancePairHist(int size, double min, double max, boolean smooth) {
        assert size >= 2
        assert min < max

        this.size = size
        this.min = min
        this.max = max
        this.smooth = smooth

        bins = new double[size]
        if (smooth) {
            step = (max - min) / (size - 1)
        } else {
            step = (max - min) / size
        }
    }

    DistancePairHist add(double dist) {
        // log.trace("add: {} step: {}", dist, step)

        count++
        if (dist<=min) {
            bins[0] += 1
            return this
        }
        if (dist>=max) {
            bins[size-1] += 1
            return this
        }

        double mod = dist - min
        int idx = (int) (mod / step) // Math.floor

        // log.trace("mod: {}, idx: {}", mod, idx)

        if (smooth) {
            // split between 2 bins according to relative closeness
            mod = mod - (idx * step)
            double ratio = mod / step
            bins[idx] += 1 - ratio
            bins[idx+1] += ratio
        } else {
            bins[idx] += 1
        }

        return this
    }

    double[] getNormalizedBins() {
        double[] res = new double[size]
        for (int i=0; i!=size; ++i) {
            res[i] = bins[i] / count
        }
        return res
    }

}
