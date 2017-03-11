package cz.siret.prank.utils.stat

import groovy.transform.CompileStatic

/**
 *
 */
@CompileStatic
class Histogram {

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
    Histogram(double min, double max, int nbins) {
        assert min <= max
        assert nbins > 0

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
        bins[findBin(value)]++
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

//===========================================================================================================//


    String toCSV(Histogram hist) {
        StringBuilder sb = new StringBuilder()

        double[] rbins = getRelativeBins()

        sb << "BIN_MAX, I, N, RATIO \n"
        for (int i=0; i!=nbins; ++i) {
            double binMax = min + (i + 1)*step
            sb << "$binMax $i, ${bins[i]}, ${rbins[i]} \n"
        }

        sb.toString()
    }

}
