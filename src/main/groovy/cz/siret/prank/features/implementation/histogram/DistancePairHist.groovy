package cz.siret.prank.features.implementation.histogram

import groovy.transform.CompileStatic

/**
 *
 */
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
        step = (max - min) / size
    }

    void add(double dist) {
        count++
        if (dist<=min) {
            bins[0] += 1
            return
        }
        if (dist>=max) {
            bins[size-1] += 1
        }

        double mod = dist - min
        int idx = (int) (mod / step)
        mod = mod - (idx * step)

        if (smooth) {
            // split between 2 bins according to relative closeness
            double ratio = mod / step
            bins[idx] += 1 - ratio
            bins[idx] += ratio
        } else {
            bins[idx] += 1
        }
    }

    double[] getNormalizedBins() {
        double[] res = new double[size]
        for (int i=0; i!=size; ++i) {
            res[i] = bins[i] / count
        }
        return res
    }


}
