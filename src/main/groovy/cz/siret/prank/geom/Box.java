package cz.siret.prank.geom;

import org.biojava.nbio.structure.Atom;

import java.util.Arrays;
import java.util.List;

/**
 * Axis-aligned bounding box over a set of atoms.
 *
 * Java port of the former {@code Box.groovy}. The hot bounding-box constructor iterated a
 * {@code List<Atom>} with a Groovy {@code for (Atom a : atoms)} which, even under
 * {@code @CompileStatic}, compiled the iterator element coercion to a per-element
 * {@code invokedynamic cast (Object) -> Atom} via Groovy's {@code CacheableCallSite}
 * (profiling on full holo4k showed this as ~1.1% of CPU, paid once per atom of every box
 * built). Rewriting in Java makes element access a plain typed {@code List.get(i)} (and
 * {@code .get(0)} instead of the {@code .first()} extension method), removing that dynamic
 * cast. The public surface is unchanged (the former Groovy properties {@code min}/{@code max}
 * are preserved as get/set accessors). Sits next to the already-Java {@link Point} and the
 * kdtree kernels, matching the repo's "hot kernel in Java" pattern.
 */
public class Box {

    private Atom min = new Point();
    private Atom max = new Point();

    private Box(List<Atom> atoms) {
        int n = atoms.size();
        if (n == 0) return;   // empty -> min/max stay at the default Point() (0,0,0), as before

        Atom b = atoms.get(0);
        double minX = b.getX(), minY = b.getY(), minZ = b.getZ();
        double maxX = minX, maxY = minY, maxZ = minZ;

        // start at 1: atom 0 already seeds min==max, so comparing it to itself is a no-op.
        // Use Math.min/Math.max (not raw `<`/`>`) so a NaN coordinate PROPAGATES into the box and
        // is caught downstream by GridGenerator's finite-box guard. This preserves the old Groovy
        // behaviour: Groovy's `>`/`<` used Double.compare semantics (NaN sorts as largest), so
        // `NaN > max` was true and the NaN leaked into the box; Java's IEEE `>` is false for NaN
        // and would silently drop it (see GridGeneratorBetweenTest.nanCoordInAtomsThrowsClearError).
        // For all finite coordinates Math.min/Math.max are identical to the comparisons.
        for (int i = 1; i < n; i++) {
            Atom a = atoms.get(i);
            double x = a.getX(), y = a.getY(), z = a.getZ();
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
        }

        min.setX(minX); min.setY(minY); min.setZ(minZ);
        max.setX(maxX); max.setY(maxY); max.setZ(maxZ);
    }

    private Box(Atom min, Atom max) {
        this.min = min;
        this.max = max;
    }

    private Box() {}

    public Atom getMin() { return min; }
    public Atom getMax() { return max; }
    public void setMin(Atom min) { this.min = min; }
    public void setMax(Atom max) { this.max = max; }

    public double getWx() {
        return max.getX() - min.getX();
    }
    public double getWy() {
        return max.getY() - min.getY();
    }
    public double getWz() {
        return max.getZ() - min.getZ();
    }

    public Atom getCenter() {
        double x = (min.getX() + max.getX()) / 2.0;
        double y = (min.getY() + max.getY()) / 2.0;
        double z = (min.getZ() + max.getZ()) / 2.0;
        return new Point(x, y, z);
    }

    public Box copy() {
        return new Box(Point.copyOf(min), Point.copyOf(max));
    }

    public Box withMargin(double margin) {
        return withMargins(margin, margin, margin);
    }

    public Box withMargins(double mx, double my, double mz) {
        return boundedBy(
            Point.of(min.getX() - mx, min.getY() - my, min.getZ() - mz),
            Point.of(max.getX() + mx, max.getY() + my, max.getZ() + mz)
        );
    }

    public boolean contains(Atom a) {
        return Struct.isInBox(a, this);
    }

    public static Box aroundAtoms(Atoms atoms) {
        return new Box(atoms.list);
    }

    public static Box boundedBy(Atom min, Atom max) {
        return new Box(min, max);
    }

    @Override
    public String toString() {
        return "box " + Arrays.toString(min.getCoords()) + " " + Arrays.toString(max.getCoords());
    }

}
