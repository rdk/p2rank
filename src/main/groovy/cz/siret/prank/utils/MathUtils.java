package cz.siret.prank.utils;

import java.util.List;
import java.util.Random;

import static cz.siret.prank.utils.StatSample.newStatSample;

public class MathUtils {

    static final double SQRT2PI = Math.sqrt(2*Math.PI);

    public static double gauss(double x, double sigma) {
        return gauss(x, 1/(sigma*SQRT2PI), sigma );
    }

    public static double gauss(double x, double a, double c) {
        return a*Math.exp(-(x*x)/(2*c*c));
    }

    public static double gaussNorm(double x, double sigma) {
        return gauss(x,sigma)/gauss(0,sigma);
    }

    public static int ranndomInt() {
        return new Random().nextInt();
    }

//===============================================================================================//

    public static double nanToZero(double x) {
        if (Double.isNaN(x)) return 0.0d;
        return x;
    }

//===========================================================================================================//

    public static double stddev(List<Double> sample) {
        return newStatSample(sample).getStddev();
    }

    public static int ceilDiv(int x, int y){
        return -Math.floorDiv(-x,y);
    }

    public static double safeDiv(double x, double y){
        if (y == 0.0d) return 0.0d;
        return x / y;
    }

//===========================================================================================================//

    /**
     * transforms <0,inf) to <0,1)
     */
    public static double sigmoid01(double x) {
        return ( 2d / (Math.exp(-x)+1d) ) - 1d;
    }

}
