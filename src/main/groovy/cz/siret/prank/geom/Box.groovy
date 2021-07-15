package cz.siret.prank.geom

import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

@CompileStatic
class Box {
    
    Atom min = new Point()
    Atom max = new Point()

    private Box(List<Atom> atoms) {
        if (atoms.empty) return

        Atom b = atoms.first()
        min.x = b.x
        max.x = b.x
        min.y = b.y
        max.y = b.y
        min.z = b.z
        max.z = b.z

        for(Atom a : atoms) {
            if (a.x < min.x) min.x = a.x
            if (a.x > max.x) max.x = a.x
            if (a.y < min.y) min.y = a.y
            if (a.y > max.y) max.y = a.y
            if (a.z < min.z) min.z = a.z
            if (a.z > max.z) max.z = a.z
        }
    }

    private Box(Atom min, Atom max) {
        this.min = min
        this.max = max
    }

    private Box() {}

    double getWx() {
        return max.x - min.x
    }
    double getWy() {
        return max.y - min.y
    }
    double getWz() {
        return max.z - min.z
    }

    Atom getCenter() {
        double x = (min.x + max.x) / 2
        double y = (min.y + max.y) / 2
        double z = (min.z + max.z) / 2

        return new Point(x,y,z)
    }

    Box copy() {
        return new Box(Point.copyOf(min), Point.copyOf(max))
    }

    Box withMargin(double margin) {
        return withMargins(margin, margin, margin)
    }

    Box withMargins(double mx, double my, double mz) {
        return boundedBy(
            Point.of(min.x - mx, min.y - my, min.z - mz),
            Point.of(max.x + mx, max.y + my, max.z + mz)
        )
    }

    boolean contains(Atom a) {
        return Struct.isInBox(a, this)
    }

    static Box aroundAtoms(Atoms atoms) {
        return new Box(atoms.list)
    }

    static Box boundedBy(Atom min, Atom max) {
        return new Box(min, max)
    }

    String toString() {
        return "box "+min.coords.toList().toListString() + " " + max.coords.toList().toListString()
    }

}