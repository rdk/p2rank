package cz.siret.prank.features;

import cz.siret.prank.utils.csv.CSV;

import java.util.List;

/**
 * superclass for all properties that can be assigned to atoms / pocket points
 */
public abstract class FeatureVector {

    public abstract double[] getArray();

    public abstract List<Double> getVector();

    public abstract List<String> getHeader();

    public String toCSV() {
        return CSV.fromDoubles(getVector()).toString();
    }

}
