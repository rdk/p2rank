package cz.siret.prank.geom

import org.biojava.nbio.structure.Atom

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

    Box enlarge(double add) {
        Box res = new Box()
        for (i in 0..2) {
            res.max.coords[i] = this.max.coords[i]+add
            res.min.coords[i] = this.min.coords[i]-add
        }
        return res
    }

    public static Box aroundAtoms(Atoms atoms) {
        return new Box(atoms.list)
    }

    String toString() {
        return "box "+min.coords.toList().toListString() + " " + max.coords.toList().toListString()
    }

}