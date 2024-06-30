package cz.siret.prank.features.generic;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import java.util.Arrays;
import java.util.List;

public class GenericVector {

    private final GenericHeader header;
    private final double[] data;

    public GenericVector(GenericHeader header) {
        this.header = header;
        this.data = new double[header.getSize()];
    }

    public GenericVector(GenericHeader header, double[] data) {
        this.header = header;
        this.data = data;
    }

    public final GenericHeader getHeader() {
        return header;
    }

    public final double[] getData() {
        return data;
    }

    public int getSize() {
        return data.length;
    }

    public double get(String colName) {
        return data[header.getColIndex(colName)];
    }

    public void set(String colName, double value) {
        Integer idx = header.getColIndex(colName);
        if (idx != null) {
            data[idx] = value;
        }
    }

    public void multiply(String colName, double a) {
        Integer idx = header.getColIndex(colName);
        if (idx != null) {
            data[idx] *= a;
        }
    }

    public void setValues(List<String> valuesHeader, double[] values) {
        String firstColName = valuesHeader.get(0);
        int start = header.getColIndex(firstColName);
        setValues(start, values);
    }

    public void setValues(int startIndex, double[] values) {
        System.arraycopy(values, 0, data, startIndex, values.length);
    }

    public List<Double> toList() {
        return DefaultGroovyMethods.toList(data);
    }

    public void addTo(List<Double> list) {
        for (int i = 0; i != data.length; i++) {
            list.add(data[i]);
        }
    }

    /**
     * @return new instance
     */
    public GenericVector copy() {
        return new GenericVector(header, Arrays.copyOf(data, data.length));//(double[]) data.clone()
    }

    /**
     * modifies instance
     */
    public GenericVector add(final GenericVector gv) {
        final double[] gvData = gv.data;
        final int n = data.length;
        for (int i = 0; i != n; ++i) {
            double toadd = gvData[i];
            //if (!Double.isNaN(toadd)) {
                data[i] += toadd;
            //}
        }

        return this;
    }

    /**
     * modifies instance
     */
    public GenericVector multiply(double a) {
        for (int i = 0; i != data.length; ++i) {
            data[i] *= a;
        }

        return this;
    }

}
