package cz.siret.prank.collectors

import cz.siret.prank.features.FeatureVector
import groovy.transform.CompileStatic

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
