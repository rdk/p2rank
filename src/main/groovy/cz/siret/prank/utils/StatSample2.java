package cz.siret.prank.utils;

import org.apache.commons.math3.stat.descriptive.moment.Kurtosis;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.Skewness;
import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.apache.commons.math3.stat.descriptive.rank.Max;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.stat.descriptive.rank.Min;
import org.apache.commons.math3.stat.descriptive.summary.Sum;

import java.util.Collection;

/**
 * Extended statistical sample with lazy evaluation of stats. Used by every
 * Methyl Cloud variant except {@code MethylEnergyCloudSF} and by the energy2
 * probe features.
 *
 * <p><b>Note:</b> {@link #getRelativeStddev()} returns stddev/mean (ratio
 * scale), unlike {@link StatSample#getRelativeStdev()} which returns
 * stddev/mean × 100 (percentage scale). Not interchangeable.
 */
public class StatSample2 {

    private final double[] data;

    private Double mean;
    private Double min;
    private Double max;
    private Double sum;
    private Double median;
    private Double variance;
    private Double stddev;
    private Double kurtosis;
    private Double skewness;



    public StatSample2(double[] data) {
        this.data = data;
    }

    public StatSample2(Collection<Double> data) {
        this.data = new double[data.size()];
        int i = 0;
        for (Double d : data) {
            this.data[i++] = d;
        }
    }

//===============================================================================================//

    public int getCount() {
        return data.length;
    }

    /**
     * Variance preserving aggregation
     */
    public double getVpa() {
        return getSum() * Math.sqrt(getCount());
    }

    public double getMin()  {
        if (min == null) {
            min = new Min().evaluate(data);
        }
        return min;
    }

    public double getMax() {
        if (max == null) {
            max = new Max().evaluate(data);
        }
        return max;
    }

    public double getSum() {
        if (sum == null) {
            sum = new Sum().evaluate(data);
        }
        return sum;
    }

    public double getMean() {
        if (mean == null) {
            mean = new Mean().evaluate(this.data);
        }
        return mean;
    }

    public double getMedian() {
        if (median == null) {
            median = new Median().evaluate(this.data);
        }
        return median;
    }

    public double getVariance() {
        if (variance == null) {
            variance = new Variance().evaluate(this.data);
        }
        return variance;
    }

    public double getStddev() {
        if (stddev == null) {
            stddev = Math.sqrt(getVariance());
        }
        return stddev;
    }

    public double getRelativeStddev() {
        return MathUtils.safeDiv(getStddev(), getMean());
    }

    public double getKurtosis() {
        if (kurtosis == null) {
            kurtosis = new Kurtosis().evaluate(this.data);
        }
        return kurtosis;
    }

    public double getSkewness() {
        if (skewness == null) {
            skewness = new Skewness().evaluate(this.data);
        }
        return skewness;
    }

}
