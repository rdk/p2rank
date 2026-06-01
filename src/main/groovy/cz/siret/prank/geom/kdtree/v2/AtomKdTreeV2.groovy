package cz.siret.prank.geom.kdtree.v2

import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.kdtree.AtomKdTree
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * AtomKdTree implementation backed by immutable KdTree3D (SoA layout, hardcoded 3D).
 * Thread-safe for concurrent queries after construction.
 */
@CompileStatic
class AtomKdTreeV2 implements AtomKdTree {

    private final KdTree3D tree

    private AtomKdTreeV2(KdTree3D tree) {
        this.tree = tree
    }

    static AtomKdTreeV2 build(Atoms atoms) {
        return new AtomKdTreeV2(KdTree3D.build(atoms))
    }

    int size() {
        return tree.size()
    }

    // --- Single nearest neighbor ---

    Atom findNearest(Atom a) {
        return tree.findNearest(a.getX(), a.getY(), a.getZ())
    }

    double nearestSqrDist(Atom a) {
        return tree.nearestSqrDist(a.getX(), a.getY(), a.getZ())
    }

    /** Raw-coordinate variant for hot paths (e.g. sparsify). */
    double nearestSqrDist(double x, double y, double z) {
        return tree.nearestSqrDist(x, y, z)
    }

    // --- Nearest different (excluding identity-equal atom) ---

    Atom findNearestDifferent(Atom a) {
        KdTree3D.NNEntry entry = singleNearestDifferent(a)
        return entry?.atom()
    }

    double nearestDifferentSqrDist(Atom a) {
        KdTree3D.NNEntry entry = singleNearestDifferent(a)
        return entry != null ? entry.sqrDist() : Double.NaN
    }

    private KdTree3D.NNEntry singleNearestDifferent(Atom a) {
        List<KdTree3D.NNEntry> entries = tree.findNearestN(a.getX(), a.getY(), a.getZ(), 2, false)
        for (KdTree3D.NNEntry entry : entries) {
            if (!(entry.atom().is(a))) {
                return entry
            }
        }
        return null
    }

    // --- k-NN ---

    Atoms findNearestNAtoms(Atom a, int count, boolean sorted) {
        return toAtoms(tree.findNearestN(a.getX(), a.getY(), a.getZ(), count, sorted))
    }

    Atoms findNearestNDifferentAtoms(Atom a, int count, boolean sorted) {
        List<KdTree3D.NNEntry> entries = tree.findNearestN(a.getX(), a.getY(), a.getZ(), count + 1, sorted)
        entries.removeIf { KdTree3D.NNEntry entry -> entry.atom().is(a) }
        if (entries.size() > count) {
            entries = entries.subList(0, count)
        }
        return toAtoms(entries)
    }

    // --- Radius search ---

    Atoms findAtomsWithinRadius(Atom a, double radius, boolean sorted) {
        double sqrRadius = radius * radius
        return tree.findWithinRadius(a.getX(), a.getY(), a.getZ(), sqrRadius)
    }

    @Override
    int countAtomsWithinRadius(Atom a, double radius) {
        double sqrRadius = radius * radius
        return tree.countWithinRadius(a.getX(), a.getY(), a.getZ(), sqrRadius)
    }

    // --- Helpers ---

    private static Atoms toAtoms(List<KdTree3D.NNEntry> entries) {
        List<Atom> list = new ArrayList<>(entries.size())
        for (KdTree3D.NNEntry e : entries) {
            list.add(e.atom())
        }
        return new Atoms(list)
    }
}
