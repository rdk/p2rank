package cz.siret.prank.utils;

import cz.siret.prank.geom.Atoms;
import org.biojava.nbio.structure.Atom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 *  Methods that needed to be written in Java for performance reasons.
 */
public class PerfUtils {

    public static double[] toPrimitiveArray(List<Double> list, double[] to) {
        final int n = list.size();
        for (int i=0; i!=n; i++) {
            to[i] = list.get(i);
        }
        return to;
    }

    public static void arrayCopy(double[] from, double[] to) {
        System.arraycopy(from, 0, to, 0, from.length);
    }

    public static double[] extendArray(double[] aa, double x) {
        double[] res = new double[aa.length+1];
        arrayCopy(aa, res);
        res[aa.length] = x;
        return res;
    }

    public static double[] toPrimitiveArray(List<Double> list) {
        return toPrimitiveArray(list, new double[list.size()]);
    }

    public static double sqrDist(final double[] a, final double[] b) {
        final double x = a[0] - b[0];
        final double y = a[1] - b[1];
        final double z = a[2] - b[2];
        return x*x + y*y + z*z;
    }

    public static double sqrDistL(Atom a, List<Atom> list) {
        if (list==null || list.isEmpty()) {
            //log.debug "!! dist to empty list of atoms"
            return Double.MAX_VALUE;
        }

        final double[] acoords = a.getCoords();

        double minDist = Double.MAX_VALUE;
        for (Atom b : list) {
            double next = sqrDist(acoords, b.getCoords());
            if (next<minDist) {
                minDist = next;
            }
        }

        return minDist;
    }

    public static double dist(double[] a, double[] b) {
        final double d = sqrDist(a, b);
        return Math.sqrt(d);
    }

    public static String formatDouble(Double d) {
        return Double.toString(fastRound(d));
    }

    public static double fastRound(double x) {
        return ((double)Math.round(x*10000)) / 10000;
    }

    public static double round(double x, int deg) {
        double p = Math.pow(10, deg);
        return ((double)Math.round(x*p)) / p;
    }

    public static String sortString(String s) {
        if (s == null) return null;
        char[] chars = s.toCharArray();
        Arrays.sort(chars);
        return new String(chars);
    }

    /**
     *
     * @return
     */
    public static boolean coversWithBreaks(String cover, String subchain) {
        Objects.requireNonNull(cover, "cover cannot be null");
        Objects.requireNonNull(subchain, "subchain cannot be null");

        int m = cover.length();
        int n = subchain.length();

        if (m < n) {
            return false;
        }
        if (n == 0) {
            return true;
        }
        // now m>0, n>0

        int i = 0; // cover
        int j = 0; // subchain
        while (i != m) {
            if (cover.charAt(i) == subchain.charAt(j)) {
                j++;
                if (j == n) {
                    return true; // whole subchain is covered
                }
            }
            i++;
        }
        return false; // whole cover is covered but subchain is not
    }

//===============================================================================================//

    public static Atoms cutoffAtomsAround(Atoms atoms, Atom distanceTo, double dist) {
        List<Atom> res = new ArrayList<>();
        double sqrDist = dist*dist;

        double[] toCoords = distanceTo.getCoords();

        for (Atom a : atoms.list) {     // this line was causing slow casting in groovy
                                        // at org.codehaus.groovy.runtime.ScriptBytecodeAdapter.castToType(ScriptBytecodeAdapter.java:599)
                                        // at rdk.pockets.geom.Atoms.cutoffAroundAtom(Atoms.groovy:219)

            if (PerfUtils.sqrDist(a.getCoords(), toCoords) <= sqrDist) {
                res.add(a);
            }
        }
        return new Atoms(res);
    }

}
