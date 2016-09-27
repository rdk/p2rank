package cz.siret.prank.collectors

import groovy.transform.CompileStatic
import cz.siret.prank.features.FeatureVector

@CompileStatic
class DoubleVector extends FeatureVector {

    List<Double> vect

    public DoubleVector(List<Double> vect) {
        this.vect = vect
    }

    @Override
    public List<Double> getVector() {
        return vect
    }

    @Override
    List<String> getHeader() {
        return []
    }

}
