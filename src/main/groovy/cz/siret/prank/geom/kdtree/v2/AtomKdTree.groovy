package cz.siret.prank.geom.kdtree.v2

import cz.siret.prank.geom.Atoms
import cz.siret.prank.geom.kdtree.AtomKdTree as AtomKdTreeV1
import cz.siret.prank.program.params.Params
import groovy.transform.CompileStatic
import org.biojava.nbio.structure.Atom

/**
 * Unified KdTree wrapper that delegates to either v1 (Rednaxela) or v2 (KdTree3D)
 * based on the kdtree_version parameter.
 *
 * Same method signatures as the original v1 AtomKdTree — Groovy callers accessing
 * via Atoms.getKdTree() are transparent (duck typing).
 *
 * v2 (default): immutable KdTree3D — no add()/addAll(), thread-safe, SoA layout.
 * v1: original Rednaxela KdTree — mutable, generic N-dimensional.
 */
@CompileStatic
class AtomKdTree {

    // Exactly one of these is non-null, depending on the selected implementation.
    private final KdTree3D tree
    private final AtomKdTreeV1 treeV1

    private AtomKdTree(KdTree3D tree) {
        this.tree = tree
        this.treeV1 = null
    }

    private AtomKdTree(AtomKdTreeV1 treeV1) {
        this.tree = null
        this.treeV1 = treeV1
    }

    static AtomKdTree build(Atoms atoms) {
        if ("AtomKdTree" == Params.INSTANCE.kdtree_implementation) {
            return new AtomKdTree(AtomKdTreeV1.build(atoms))
        }
        return new AtomKdTree(KdTree3D.build(atoms))
    }

    int size() {
        return tree != null ? tree.size() : treeV1.size()
    }

    // --- Single nearest neighbor ---

    Atom findNearest(Atom a) {
        if (tree != null) {
            return tree.findNearest(a.getX(), a.getY(), a.getZ())
        }
        return treeV1.findNearest(a)
    }

    double nearestDist(Atom a) {
        return Math.sqrt(nearestSqrDist(a))
    }

    double nearestSqrDist(Atom a) {
        if (tree != null) {
            return tree.nearestSqrDist(a.getX(), a.getY(), a.getZ())
        }
        return treeV1.nearestSqrDist(a)
    }

    // --- Nearest different (excluding identity-equal atom) ---

    Atom findNearestDifferent(Atom a) {
        if (tree != null) {
            KdTree3D.NNEntry entry = singleNearestDifferent(a)
            return entry?.atom()
        }
        return treeV1.findNearestDifferent(a)
    }

    double nearestDifferentDist(Atom a) {
        return Math.sqrt(nearestDifferentSqrDist(a))
    }

    double nearestDifferentSqrDist(Atom a) {
        if (tree != null) {
            KdTree3D.NNEntry entry = singleNearestDifferent(a)
            return entry != null ? entry.sqrDist() : Double.NaN
        }
        return treeV1.nearestDifferentSqrDist(a)
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
        if (tree != null) {
            return toAtoms(tree.findNearestN(a.getX(), a.getY(), a.getZ(), count, sorted))
        }
        return treeV1.findNearestNAtoms(a, count, sorted)
    }

    Atoms findNearestNDifferentAtoms(Atom a, int count, boolean sorted) {
        if (tree != null) {
            List<KdTree3D.NNEntry> entries = tree.findNearestN(a.getX(), a.getY(), a.getZ(), count + 1, sorted)
            entries.removeIf { KdTree3D.NNEntry entry -> entry.atom().is(a) }
            if (entries.size() > count) {
                entries = entries.subList(0, count)
            }
            return toAtoms(entries)
        }
        return treeV1.findNearestNDifferentAtoms(a, count, sorted)
    }

    // --- Radius search ---

    Atoms findAtomsWithinRadius(Atom a, double radius, boolean sorted) {
        if (tree != null) {
            double sqrRadius = radius * radius
            return tree.findWithinRadius(a.getX(), a.getY(), a.getZ(), sqrRadius)
        }
        return treeV1.findAtomsWithinRadius(a, radius, sorted)
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
