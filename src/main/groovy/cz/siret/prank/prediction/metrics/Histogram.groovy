package cz.siret.prank.prediction.metrics


import groovy.transform.CompileStatic

import static cz.siret.prank.utils.Formatter.format
import static cz.siret.prank.utils.Formatter.format0

/**
 *
 */
@CompileStatic
class Histogram {

    String label

    double min
    double max
    int nbins
    double step

    long[] bins
    long count = 0

    /**
     *
     * @param min
     * @param max
     * @param nbins bin count
     */
    Histogram(String label, double min, double max, int nbins) {
        assert min <= max
        assert nbins > 0

        this.label = label

        this.min = min
        this.max = max
        this.nbins = nbins

        step = (max - min) / nbins
        bins = new long[nbins]
    }

    private int findBin(double value) {
        if (value<=min)
            return 0
        if (value>=max)
            return nbins-1

        (int) ((value - min) / step)
    }

    void put(double value) {
        int bin = findBin(value)
        bins[bin] = bins[bin]+1
        count++               
    }

    /**
     * relative bins that sum up to 1
     * @return
     */
    double[] getRelativeBins() {
        double[] rbins = new double[nbins]
        for (int i=0; i!=nbins; ++i) {
            rbins[i] = (double)bins[i] / count
        }
        return rbins
    }

    /**
     * Add counts from other to this histogram.
     * Number of bins of both must be the same.
     * @param hist2
     */
    void add(Histogram other) {
        assert nbins==other.nbins : "Unequal number of bins!"

        count += other.count
        for (int i=0; i!=nbins; ++i) {
            bins[i] = bins[i] + other.bins[i]
        }
    }

//===========================================================================================================//

    String toCSV() {
        StringBuilder sb = new StringBuilder()

        double[] rbins = getRelativeBins()

        sb << "BIN_MIN, BIN_MAX, I, N, RATIO \n"
        for (int i=0; i!=nbins; ++i) {
            double binMin = min + (i)*step
            double binMax = min + (i + 1)*step
            sb << "${format0(binMin,3)}, ${format0(binMax,3)}, $i, ${bins[i]}, ${format(rbins[i],7)} \n"
        }

        sb.toString()
    }

}
