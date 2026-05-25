package cz.siret.prank.utils;

import cz.siret.prank.geom.Atoms;
import cz.siret.prank.geom.Point;
import org.biojava.nbio.structure.Atom;
import org.biojava.nbio.structure.AtomImpl;

import java.util.*;

import org.biojava.nbio.structure.Calc;

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


    public static double dist(double[] a, double[] b) {
        final double d = sqrDist(a, b);
        return Math.sqrt(d);
    }

    public static double sqrDist(Atom a, Atom b) {
        final double x = a.getX() - b.getX();
        final double y = a.getY() - b.getY();
        final double z = a.getZ() - b.getZ();
        return x*x + y*y + z*z;
    }

    public static double dist(Atom a, Atom b) {
        final double x = a.getX() - b.getX();
        final double y = a.getY() - b.getY();
        final double z = a.getZ() - b.getZ();
        return Math.sqrt(x*x + y*y + z*z);
    }

    /**
     * Java equivalent of Struct.areWithinDistance. The Groovy version's inner
     * loop went through invokedynamic and serialized all concurrent callers on
     * a single CacheableCallSite monitor, capping multi-threaded throughput.
     */
    public static boolean areWithinDistance(Atom a, List<Atom> list, double dist) {
        final double sqr = dist * dist;
        final double ax = a.getX();
        final double ay = a.getY();
        final double az = a.getZ();
        for (Atom b : list) {
            final double dx = ax - b.getX();
            final double dy = ay - b.getY();
            final double dz = az - b.getZ();
            if (dx*dx + dy*dy + dz*dz <= sqr) {
                return true;
            }
        }
        return false;
    }

    /** Java equivalent of Struct.areDistantAtLeast. See areWithinDistance for context. */
    public static boolean areDistantAtLeast(Atom a, List<Atom> list, double dist) {
        final double sqr = dist * dist;
        final double ax = a.getX();
        final double ay = a.getY();
        final double az = a.getZ();
        for (Atom b : list) {
            final double dx = ax - b.getX();
            final double dy = ay - b.getY();
            final double dz = az - b.getZ();
            if (dx*dx + dy*dy + dz*dz < sqr) {
                return false;
            }
        }
        return true;
    }

    public static double sqrDistL(Atom a, List<Atom> list) {
        if (list==null || list.isEmpty()) {
            //log.debug "!! dist to empty list of atoms"
            return Double.MAX_VALUE;
        }

        final double ax = a.getX();
        final double ay = a.getY();
        final double az = a.getZ();

        double dx;
        double dy;
        double dz;

        double minDist = Double.MAX_VALUE;
        for (Atom b : list) {
            dx = ax - b.getX();
            dy = ay - b.getY();
            dz = az - b.getZ();


            double next = dx*dx + dy*dy + dz*dz;

            if (next < minDist) {
                minDist = next;
            }
        }

        return minDist;
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

        for (Atom a : atoms.list) {     // this line was causing slow casting in groovy
                                        // at org.codehaus.groovy.runtime.ScriptBytecodeAdapter.castToType(ScriptBytecodeAdapter.java:599)
                                        // at rdk.pockets.geom.Atoms.cutoffAroundAtom(Atoms.groovy:219)

            if (PerfUtils.sqrDist(a, distanceTo) <= sqrDist) {
                res.add(a);
            }
        }
        return new Atoms(res);
    }

//===============================================================================================//

	/**
	 * Returns the center of mass of the set of atoms. Atomic masses of the
	 * Atoms are used.
     *
     * Based on @see org.biojava.nbio.structure.Calc#centerOfMass(org.biojava.nbio.structure.Atom[])
	 *
	 * @return an Atom representing the center of mass
	 */
	public static Atom centerOfMass(Collection<? extends Atom> atoms) {
        if (atoms.isEmpty()) {
            return null;
        }

		Atom center = new AtomImpl();

		double totalMass = 0.0d;
		for (Atom a : atoms) {
			float mass = a.getElement().getAtomicMass();
			totalMass += mass;
			Calc.scaleAdd(mass, a, center);
		}

		Calc.scaleEquals(center, 1.0d/totalMass);
		return center;
	}


	/**
	 * Returns the centroid of the set of atoms.
	 *
	 * Based on @see org.biojava.nbio.structure.Calc#getCentroid(org.biojava.nbio.structure.Atom[])
     *
	 * @return an Atom representing the Centroid of the set of atoms
	 */
    public static Atom calculateCentroid(Collection<? extends Atom> atoms) {
		if (atoms.isEmpty()) {
			return null;
        }

        double x = 0.0d;
        double y = 0.0d;
        double z = 0.0d;

        for (Atom a : atoms) {
            x += a.getX();
            y += a.getY();
            z += a.getZ();
        }

        int n = atoms.size();
        x /= n;
        y /= n;
        z /= n;

        return new Point(x, y, z);
    }

}
