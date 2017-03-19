package cz.siret.prank.collectors

import cz.siret.prank.features.FeatureVector
import cz.siret.prank.utils.PerfUtils
import groovy.transform.CompileStatic

@CompileStatic
class DoubleVector extends FeatureVector {

    double[] data

    public DoubleVector(List<Double> vect) {
        this.data = PerfUtils.toPrimitiveArray(vect)
    }

    public DoubleVector(double[] data) {
        this.data = data
    }

    @Override
    double[] getArray() {
        return data
    }

    @Override
    public List<Double> getVector() {
        return data.toList()
    }

    @Override
    List<String> getHeader() {
        return []
    }

}
