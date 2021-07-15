package cz.siret.prank.utils

import groovy.transform.CompileStatic

import static java.lang.Math.sqrt

/**
 * Double vector with basic stats
 */
@CompileStatic
class StatSample {

    Collection<Double> sample

    StatSample(Collection<Double> sample) {
        assert sample != null
        assert !sample.isEmpty()
        
        this.sample = sample
    }

    static StatSample newStatSample(Collection<Double> sample) {
        new StatSample(sample)
    }

    double getSum() {
        double sum = 0
        for (double x : sample) {
            sum += x
        }
        sum
    }

    int getSize() {
        sample.size()
    }

    double getMean() {
        if (size==0) return 0
        sum / size
    }

    double getVariance() {
        double mean = mean
        double xx = 0
        for (double a : sample)
            xx += (a - mean) * (a - mean)
        xx / (size - 1)
    }

    double getStddev() {
        sqrt(variance)
    }
    
    double getRelativeStdev() {
        (100*stddev) / mean
    }

    double getMin() {
        Collections.min(sample)
    }

    double getMax() {
        Collections.max(sample)
    }

}
