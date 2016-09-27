package cz.siret.prank.features

import groovy.transform.CompileStatic
import cz.siret.prank.utils.CSV

/**
 * superclass for all properties that can be assigned to atoms / pocket points
 */
@CompileStatic
abstract class FeatureVector {

    public String toCSV() {
        return CSV.fromDoubles(getVector())
    }

    abstract List<Double> getVector();

    abstract List<String> getHeader()

}
