package cz.siret.prank.features

import cz.siret.prank.utils.CSV
import groovy.transform.CompileStatic

/**
 * superclass for all properties that can be assigned to atoms / pocket points
 */
@CompileStatic
abstract class FeatureVector {

    abstract double[] getArray()

    abstract List<Double> getVector()

    abstract List<String> getHeader()

    public String toCSV() {
        return CSV.fromDoubles(getVector())
    }
    
}
